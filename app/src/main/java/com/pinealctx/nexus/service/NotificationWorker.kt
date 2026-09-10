package com.pinealctx.nexus.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pinealctx.nexus.core.*
import com.pinealctx.nexus.core.managers.ConversationManager
import com.pinealctx.nexus.core.managers.PushManager
import com.pinealctx.nexus.local.LocalDataStore
import com.pinealctx.nexus.local.NotificationLedger
import com.pinealctx.nexus.util.NotificationHelper
import com.shared.v1.PushPlatform
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException

class NotificationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, NotificationDependencies::class.java)
        val data = inputData.keyValueMap.mapValues { it.value.toString() }
        val owner = data["recipient_id"]?.toIntOrNull() ?: return Result.success()
        val helper = deps.notifications()
        if (!helper.matchesSession(owner, data["session"].orEmpty())) return Result.success()
        return try {
            when (data["type"]) {
                "register" -> deps.push().registerPushToken(data["registration"].orEmpty(), PushPlatform.PUSH_PLATFORM_FCM.number)
                "reply", "mark_read" -> {
                    val conversationId = data["conversation_id"]?.toLongOrNull() ?: return Result.failure()
                    val messageId = data["message_id"]?.toLongOrNull() ?: return Result.failure()
                    if (data["type"] == "reply") {
                        val text = data["reply_text"].orEmpty().trim()
                        if (text.isEmpty()) return Result.success()
                        val clientId = data["client_message_id"]?.toLongOrNull() ?: return Result.failure()
                        if (!helper.matchesSession(owner, data["session"].orEmpty())) return Result.success()
                        deps.cache().enqueueNotificationReply(LocalMessageData(clientId, "$conversationId", null, owner, MessageContent.Text(text), null, System.currentTimeMillis(), MessageSendState.SENDING))
                        if (deps.cache().getLocalMessage(clientId)?.sendState == MessageSendState.SENDING) deps.sends().enqueue(clientId)
                    }
                    if (!helper.matchesSession(owner, data["session"].orEmpty())) return Result.success()
                    deps.conversations().markAsRead(conversationId, messageId)
                }
                else -> helper.process(data)
            }
            Result.success()
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { if (runAttemptCount < 8) Result.retry() else Result.failure() }
    }
}

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val deps = EntryPointAccessors.fromApplication(context.applicationContext, NotificationDependencies::class.java)
        val owner = intent.getStringExtra("recipient_id")?.toIntOrNull() ?: return
        val session = intent.getStringExtra("session").orEmpty()
        if (!deps.notifications().matchesSession(owner, session)) return
        val kind = when (intent.action) { "reply" -> "reply"; "read" -> "mark_read"; else -> return }
        val data = mutableMapOf("type" to kind, "recipient_id" to "$owner", "session" to session,
            "conversation_id" to intent.getStringExtra("conversation_id").orEmpty(), "message_id" to intent.getStringExtra("message_id").orEmpty())
        if (kind == "reply") {
            data["reply_text"] = RemoteInput.getResultsFromIntent(intent)?.getCharSequence("reply_text")?.toString().orEmpty()
            data["client_message_id"] = (java.util.UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE).coerceAtLeast(1).toString()
        }
        deps.notifications().enqueue(data)
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NotificationDependencies {
    fun notifications(): NotificationHelper
    fun push(): PushManager
    fun conversations(): ConversationManager
    fun cache(): LocalDataStore
    fun ledger(): NotificationLedger
    fun sends(): MessageSendScheduler
}
