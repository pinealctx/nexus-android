package com.pinealctx.nexus.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.pinealctx.nexus.MainActivity
import com.pinealctx.nexus.R
import com.pinealctx.nexus.core.AppPreferences
import com.shared.v1.MessageType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences
) {
    companion object {
        const val CHANNEL_ID_MESSAGES = "nexus_messages"
        const val CHANNEL_ID_FRIEND_REQUESTS = "nexus_friend_requests"
        private const val FRIEND_REQUEST_NOTIFICATION_ID = 900
        private const val MESSAGE_NOTIFICATION_ID_BASE = 10_000
        private const val REMOTE_DEDUP_WINDOW_MS = 30_000L
        private val nextNotificationId = AtomicInteger(1000)
    }

    private data class RemoteMessageMarker(val markedAt: Long, val count: Int)

    private val recentRemoteMessages = ConcurrentHashMap<String, RemoteMessageMarker>()

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val messageChannel = NotificationChannel(
            CHANNEL_ID_MESSAGES,
            context.getString(R.string.notification_channel_messages),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_messages_desc)
            enableVibration(true)
            setShowBadge(true)
        }

        val friendRequestChannel = NotificationChannel(
            CHANNEL_ID_FRIEND_REQUESTS,
            context.getString(R.string.notification_channel_friend_requests),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_friend_requests_desc)
            enableVibration(true)
            setShowBadge(true)
        }

        notificationManager.createNotificationChannels(
            listOf(messageChannel, friendRequestChannel)
        )
    }

    suspend fun showMessageNotification(
        senderName: String,
        messageText: String,
        conversationId: String? = null
    ) {
        val settings = appPreferences.currentSettings()
        if (!settings.notificationAlerts) return
        if (!canPostNotifications()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            conversationId?.let { putExtra("conversationId", it) }
        }
        val notificationId = messageNotificationId(conversationId)

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_MESSAGES)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(senderName)
            .setContentText(messageText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setSilent(!settings.notificationSound)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    suspend fun showFriendRequestNotification(fromUserName: String, message: String?) {
        val settings = appPreferences.currentSettings()
        if (!settings.notificationAlerts) return
        if (!canPostNotifications()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigateTo", "friend_requests")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            FRIEND_REQUEST_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (!message.isNullOrBlank()) {
            "$fromUserName: $message"
        } else {
            context.getString(R.string.notification_friend_request_body, fromUserName)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_FRIEND_REQUESTS)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(context.getString(R.string.notification_friend_request_title))
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setSilent(!settings.notificationSound)
            .build()

        notificationManager.notify(FRIEND_REQUEST_NOTIFICATION_ID, notification)
    }

    fun cancelAll() {
        notificationManager.cancelAll()
    }

    fun cancelNotification(id: Int) {
        notificationManager.cancel(id)
    }

    fun newMessageTitle(): String = context.getString(R.string.notification_new_message)

    fun newMessageBody(): String = context.getString(R.string.notification_new_message_body)

    fun messagePreview(message: String?, messageType: Int): String {
        if (!message.isNullOrBlank()) return message
        return when (MessageType.forNumber(messageType)) {
            MessageType.MESSAGE_TYPE_IMAGE -> context.getString(R.string.notification_message_image)
            MessageType.MESSAGE_TYPE_AUDIO -> context.getString(R.string.notification_message_audio)
            MessageType.MESSAGE_TYPE_VIDEO -> context.getString(R.string.notification_message_video)
            MessageType.MESSAGE_TYPE_FILE -> context.getString(R.string.notification_message_file)
            MessageType.MESSAGE_TYPE_CARD -> context.getString(R.string.notification_message_card)
            else -> newMessageBody()
        }
    }

    fun markRemoteMessage(conversationId: String) {
        val now = SystemClock.elapsedRealtime()
        recentRemoteMessages.compute(conversationId) { _, marker ->
            if (marker != null && now - marker.markedAt <= REMOTE_DEDUP_WINDOW_MS) {
                RemoteMessageMarker(now, marker.count + 1)
            } else {
                RemoteMessageMarker(now, 1)
            }
        }
    }

    fun shouldSuppressSyncedMessage(conversationId: String): Boolean {
        var suppress = false
        val now = SystemClock.elapsedRealtime()
        recentRemoteMessages.computeIfPresent(conversationId) { _, marker ->
            if (now - marker.markedAt > REMOTE_DEDUP_WINDOW_MS) {
                null
            } else {
                suppress = true
                if (marker.count == 1) null else marker.copy(count = marker.count - 1)
            }
        }
        return suppress
    }

    private fun messageNotificationId(conversationId: String?): Int =
        conversationId?.let { MESSAGE_NOTIFICATION_ID_BASE + (it.hashCode() and 0x3fffffff) }
            ?: nextNotificationId.getAndIncrement()

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}
