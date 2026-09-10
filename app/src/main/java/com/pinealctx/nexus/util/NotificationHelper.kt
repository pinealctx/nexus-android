package com.pinealctx.nexus.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.work.*
import coil3.toBitmap
import com.pinealctx.nexus.MainActivity
import com.pinealctx.nexus.R
import com.pinealctx.nexus.client.toMessageData
import com.pinealctx.nexus.core.*
import com.pinealctx.nexus.local.LocalDataStore
import com.pinealctx.nexus.local.NotificationLedger
import com.pinealctx.nexus.service.NotificationActionReceiver
import com.pinealctx.nexus.service.NotificationWorker
import com.shared.v1.MessageType
import com.shared.v1.SnUpdate
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: AppPreferences,
    private val secure: SecureStorage,
    private val cache: LocalDataStore,
    private val ledger: NotificationLedger
) {
    @Volatile var isForeground = false
    @Volatile var activeConversationId: String? = null
    private val mutex = Mutex()
    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        manager.createNotificationChannels(listOf(
            NotificationChannel(CHANNEL_ID_MESSAGES, context.getString(R.string.notification_channel_messages), NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(CHANNEL_ID_FRIEND_REQUESTS, context.getString(R.string.notification_channel_friend_requests), NotificationManager.IMPORTANCE_DEFAULT)
        ))
    }

    fun enqueue(values: Map<String, String>) {
        if (!secure.hasTokens()) return
        val data = Data.Builder()
        values.forEach { (key, value) -> data.putString(key, value.take(2000)) }
        data.putString("session", values["session"] ?: secure.notificationSession()).putString("recipient_id", values["recipient_id"] ?: secure.getUserId().toString())
        val builder = OneTimeWorkRequestBuilder<NotificationWorker>()
            .setInputData(data.build()).addTag(WORK_TAG)
        if (values["type"] in listOf("message", "friend_request", "reaction", "reply", "mark_read")) builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        val request = builder.build()
        val lane = if (values["type"] in listOf("register", "reply", "mark_read")) "${values["type"]}_${values["conversation_id"].orEmpty()}" else "display"
        WorkManager.getInstance(context).enqueueUniqueWork("nexus_notifications_${secure.getUserId()}_$lane", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    fun onUpdate(update: SnUpdate, alert: Boolean) {
        val owner = secure.getUserId()
        if (owner <= 0) return
        val values = mutableMapOf("event_id" to "$owner:${update.sn}", "recipient_id" to "$owner")
        when (update.updateCase) {
            SnUpdate.UpdateCase.MESSAGE_ENVELOPE -> {
                val env = update.messageEnvelope
                val conversation = cache.getConversation(env.conversationId)
                values["conversation_id"] = "${env.conversationId}"
                values["message_id"] = "${env.messageId}"
                values["created_at"] = "${env.createdAt}"
                if (env.body.type == MessageType.MESSAGE_TYPE_RECALLED) values["type"] = "remove_message"
                else {
                    if (!alert || !NotificationPolicy.shouldAlert(env, owner, conversation?.isMuted == true)) return
                    values["type"] = "message"
                    values["sender_id"] = "${env.senderId}"
                    values["sender_name"] = cache.getUser(env.senderId)?.let { it.alias ?: it.nickname } ?: newMessageTitle()
                    values["avatar_url"] = cache.getUser(env.senderId)?.avatarUrl.orEmpty()
                    values["message"] = env.toMessageData().previewText()
                    values["message_type"] = "${env.body.type.number}"
                    values["group_name"] = if (env.conversationId shr 32 == 0L) conversation?.displayName.orEmpty() else ""
                    values["mention"] = (!NotificationPolicy.shouldAlert(env, owner, true)).not().toString()
                }
            }
            SnUpdate.UpdateCase.READ_RECEIPT -> {
                values["type"] = "read"
                values["conversation_id"] = "${update.readReceipt.conversationId}"
                values["message_id"] = "${update.readReceipt.lastReadMessageId}"
            }
            SnUpdate.UpdateCase.MESSAGE_DELETED -> {
                values["type"] = "delete"
                values["conversation_id"] = "${update.messageDeleted.conversationId}"
                values["message_ids"] = update.messageDeleted.messageIdsList.joinToString(",")
                values["message_id"] = "${update.messageDeleted.upToMessageId}"
            }
            SnUpdate.UpdateCase.MESSAGE_REACTIONS_CHANGED -> {
                val event = update.messageReactionsChanged
                if (!alert || !event.present || event.actorId == owner || event.messageSenderId != owner) return
                values["type"] = "reaction"
                values["conversation_id"] = "${event.state.conversationId}"
                values["message_id"] = "${event.state.messageId}"
                values["revision"] = "${event.state.revision}"
                values["sender_id"] = "${event.actorId}"
                values["sender_name"] = cache.getUser(event.actorId)?.nickname ?: newMessageTitle()
                values["emoji"] = event.emoji
                values["created_at"] = "${event.createdAt}"
            }
            SnUpdate.UpdateCase.FRIEND_REQUEST_RECEIVED -> {
                if (!alert) return
                val request = update.friendRequestReceived.request
                values["type"] = "friend_request"
                values["request_id"] = "${request.requestId}"
                values["sender_name"] = cache.getUser(request.fromUserId)?.nickname ?: newMessageTitle()
                values["message"] = request.message
            }
            else -> return
        }
        enqueue(values)
    }

    suspend fun process(data: Map<String, String>) = mutex.withLock {
        val owner = data["recipient_id"]?.toIntOrNull() ?: return@withLock
        val session = data["session"].orEmpty()
        if (!matchesSession(owner, session)) return@withLock
        val conversationId = data["conversation_id"].orEmpty()
        val messageId = data["message_id"]?.toLongOrNull() ?: 0L
        val kind = data["type"]
        if (kind == "sync") return@withLock
        if (kind in listOf("read", "delete", "remove_message")) {
            if (kind == "read" || kind == "delete") ledger.markRead(owner, conversationId, messageId)
            val ids = data["message_ids"].orEmpty().split(',').toSet()
            if (kind == "remove_message") ledger.accept(owner, "message:$conversationId:$messageId")
            if (kind == "delete") ids.filter { it.isNotBlank() }.forEach { ledger.accept(owner, "message:$conversationId:$it") }
            val rows = ledger.messages(owner, conversationId).filterNot {
                val id = it["message_id"]?.toLongOrNull() ?: 0L
                when (kind) {
                    "remove_message" -> id == messageId
                    "delete" -> id.toString() in ids || id <= messageId
                    else -> id <= messageId
                }
            }
            ledger.save(owner, conversationId, rows)
            if (rows.isEmpty()) manager.cancel(tag(owner, conversationId), 1)
            else render(owner, session, conversationId, rows, silent = true)
            return@withLock
        }
        val settings = preferences.currentSettings()
        if (!matchesSession(owner, session) || !settings.notificationAlerts || !canPost()) return@withLock
        if (kind == "reaction") {
            if (!settings.notificationReactions || conversationId.toLongOrNull() == null || messageId <= 0) return@withLock
            if (cache.getConversation(conversationId.toLong())?.isMuted == true || isForeground && activeConversationId == conversationId) return@withLock
            val revision = data["revision"]?.toLongOrNull() ?: return@withLock
            val current = cache.getReactionState(conversationId, messageId)?.view?.state?.revision ?: 0
            if (current > revision || System.currentTimeMillis() - (data["created_at"]?.toLongOrNull() ?: 0) > 24 * 60 * 60 * 1000L) return@withLock
            val eventKey = "reaction:$conversationId:$messageId:$revision"
            if (ledger.contains(owner, eventKey)) return@withLock
            val open = intent(owner, session).putExtra("conversationId", conversationId).putExtra("messageId", "$messageId")
                .setData(android.net.Uri.parse("nexus://notification/$owner/$conversationId/$messageId/reaction"))
            val notification = NotificationCompat.Builder(context, CHANNEL_ID_MESSAGES)
                .setSmallIcon(R.drawable.ic_notification).setContentTitle(context.getString(R.string.notification_reaction_title))
                .setContentText(if (settings.notificationPreview) context.getString(R.string.notification_reaction_body, data["sender_name"].orEmpty(), data["emoji"].orEmpty()) else newMessageBody())
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setSilent(!settings.notificationSound).setAutoCancel(true).setOnlyAlertOnce(true)
                .setContentIntent(PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)).build()
            manager.notify("reaction_${owner}_${conversationId}_$messageId", 2, notification)
            ledger.accept(owner, eventKey)
            return@withLock
        }
        if (kind == "friend_request") {
            val eventKey = "friend:${data["request_id"] ?: data["event_id"]}"
            if (!settings.notificationFriends || ledger.contains(owner, eventKey)) return@withLock
            val intent = intent(owner, session).putExtra("navigateTo", "friend_requests")
            val notification = NotificationCompat.Builder(context, CHANNEL_ID_FRIEND_REQUESTS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.notification_friend_request_title))
                .setContentText(if (settings.notificationPreview) "${data["sender_name"]}: ${data["message"].orEmpty()}" else newMessageBody())
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setAutoCancel(true).setSilent(!settings.notificationSound)
                .setContentIntent(PendingIntent.getActivity(context, 900, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)).build()
            manager.notify("friend_$owner", 900, notification)
            ledger.accept(owner, eventKey)
            return@withLock
        }
        if (kind != "message" || messageId <= 0 || conversationId.isBlank()) return@withLock
        val conversation = conversationId.toLongOrNull()?.let(cache::getConversation)
        if (data["sender_id"]?.toIntOrNull() == owner || maxOf(conversation?.lastReadMessageId ?: 0, ledger.readWaterline(owner, conversationId)) >= messageId) return@withLock
        if (conversation?.isMuted == true && data["mention"] != "true") return@withLock
        if (isForeground && activeConversationId == conversationId) return@withLock
        val created = data["created_at"]?.toLongOrNull() ?: System.currentTimeMillis()
        if (System.currentTimeMillis() - created > 24 * 60 * 60 * 1000L) return@withLock
        val eventKey = "message:$conversationId:$messageId"
        if (ledger.contains(owner, eventKey)) return@withLock
        val previous = ledger.messages(owner, conversationId)
        val rows = (previous + data).distinctBy { it["message_id"] }.sortedBy { it["message_id"]?.toLongOrNull() ?: 0 }
        ledger.save(owner, conversationId, rows)
        render(owner, session, conversationId, rows.takeLast(8), silent = previous.any { it["message_id"] == "$messageId" })
        ledger.accept(owner, eventKey)
    }

    private suspend fun render(owner: Int, session: String, conversationId: String, rows: List<Map<String, String>>, silent: Boolean) {
        val settings = preferences.currentSettings()
        if (!matchesSession(owner, session)) return
        val last = rows.last()
        val title = last["group_name"].orEmpty().ifBlank { last["sender_name"].orEmpty().ifBlank { newMessageTitle() } }
        val messageId = last["message_id"].orEmpty()
        val open = intent(owner, session).putExtra("conversationId", conversationId).putExtra("messageId", messageId)
        open.data = android.net.Uri.parse("nexus://notification/$owner/$conversationId/$messageId")
        val shortcutId = tag(owner, conversationId)
        val me = Person.Builder().setName(context.getString(R.string.notification_you)).setKey("$owner").build()
        val style = NotificationCompat.MessagingStyle(me).setGroupConversation(last["group_name"].orEmpty().isNotBlank())
        if (last["group_name"].orEmpty().isNotBlank()) style.setConversationTitle(title)
        rows.forEach { row ->
            val person = Person.Builder().setName(row["sender_name"].orEmpty().ifBlank { newMessageTitle() }).setKey(row["sender_id"])
                .setIcon(cachedAvatar(row["avatar_url"])).build()
            style.addMessage(if (settings.notificationPreview) messagePreview(row["message"], row["message_type"]?.toIntOrNull() ?: 0) else newMessageBody(), row["created_at"]?.toLongOrNull() ?: System.currentTimeMillis(), person)
        }
        ShortcutManagerCompat.pushDynamicShortcut(context, ShortcutInfoCompat.Builder(context, shortcutId)
            .setShortLabel(title.take(40)).setLongLived(true).setIntent(open.setAction(Intent.ACTION_VIEW)).build())
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setStyle(style)
            .setShortcutId(shortcutId).setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setAutoCancel(true).setSilent(silent || !settings.notificationSound)
            .addAction(action("read", owner, session, conversationId, messageId, R.string.notification_mark_read))
            .addAction(action("reply", owner, session, conversationId, messageId, R.string.notification_reply))
            .build()
        manager.notify(shortcutId, 1, notification)
    }

    private fun action(kind: String, owner: Int, session: String, conversationId: String, messageId: String, label: Int): NotificationCompat.Action {
        val intent = Intent(context, NotificationActionReceiver::class.java).setAction(kind)
            .setData(android.net.Uri.parse("nexus://notification-action/$owner/$conversationId/$messageId/$kind"))
            .putExtra("recipient_id", "$owner").putExtra("session", session)
            .putExtra("conversation_id", conversationId).putExtra("message_id", messageId)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (kind == "reply") PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
        val builder = NotificationCompat.Action.Builder(0, context.getString(label), PendingIntent.getBroadcast(context, 0, intent, flags))
        if (kind == "reply") builder.addRemoteInput(RemoteInput.Builder("reply_text").setLabel(context.getString(label)).build())
        return builder.setSemanticAction(if (kind == "reply") NotificationCompat.Action.SEMANTIC_ACTION_REPLY else NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ).setShowsUserInterface(false).build()
    }

    fun matchesSession(owner: Int, session: String): Boolean = secure.hasTokens() && secure.getUserId() == owner && secure.notificationSession() == session
    fun clearRead(conversationId: Long, messageId: Long) {
        enqueue(mapOf("type" to "read", "conversation_id" to "$conversationId", "message_id" to "$messageId"))
    }
    private suspend fun cachedAvatar(url: String?): androidx.core.graphics.drawable.IconCompat? {
        if (url.isNullOrBlank()) return null
        return try {
            val result = coil3.SingletonImageLoader.get(context).execute(coil3.request.ImageRequest.Builder(context)
                .data(url).size(96).networkCachePolicy(coil3.request.CachePolicy.DISABLED).build())
            (result as? coil3.request.SuccessResult)?.image?.let { androidx.core.graphics.drawable.IconCompat.createWithBitmap(it.toBitmap()) }
        } catch (error: kotlinx.coroutines.CancellationException) { throw error } catch (_: Exception) { null }
    }
    fun clearConversation(conversationId: String) {
        ledger.save(secure.getUserId(), conversationId, emptyList())
        manager.cancel(tag(secure.getUserId(), conversationId), 1)
    }
    fun cancelAll() { manager.cancelAll(); ledger.clear(); WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG) }
    fun newMessageTitle(): String = context.getString(R.string.notification_new_message)
    fun newMessageBody(): String = context.getString(R.string.notification_new_message_body)
    fun messagePreview(message: String?, messageType: Int): String {
        if (!message.isNullOrBlank()) return NotificationPolicy.plainPreview(message)
        return context.getString(when (MessageType.forNumber(messageType)) {
            MessageType.MESSAGE_TYPE_IMAGE -> R.string.notification_message_image
            MessageType.MESSAGE_TYPE_AUDIO -> R.string.notification_message_audio
            MessageType.MESSAGE_TYPE_VIDEO -> R.string.notification_message_video
            MessageType.MESSAGE_TYPE_FILE -> R.string.notification_message_file
            MessageType.MESSAGE_TYPE_CARD -> R.string.notification_message_card
            else -> R.string.notification_new_message_body
        })
    }
    private fun intent(owner: Int, session: String) = Intent(context, MainActivity::class.java)
        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        .putExtra("recipient_id", "$owner").putExtra("session", session)
    private fun tag(owner: Int, conversationId: String) = "nexus_${owner}_$conversationId"
    private fun canPost() = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    companion object {
        const val CHANNEL_ID_MESSAGES = "nexus_messages"
        const val CHANNEL_ID_FRIEND_REQUESTS = "nexus_friend_requests"
        const val WORK_TAG = "nexus_notification"
    }
}
