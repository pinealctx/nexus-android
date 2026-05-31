package com.pinealctx.nexus.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.pinealctx.nexus.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_ID_MESSAGES = "nexus_messages"
        const val CHANNEL_NAME_MESSAGES = "Messages"
        const val CHANNEL_DESCRIPTION_MESSAGES = "New message notifications"

        const val CHANNEL_ID_FRIEND_REQUESTS = "nexus_friend_requests"
        const val CHANNEL_NAME_FRIEND_REQUESTS = "Friend Requests"
        const val CHANNEL_DESCRIPTION_FRIEND_REQUESTS = "Friend request notifications"

        private var notificationId = 1000
    }

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val messageChannel = NotificationChannel(
            CHANNEL_ID_MESSAGES,
            CHANNEL_NAME_MESSAGES,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = CHANNEL_DESCRIPTION_MESSAGES
            enableVibration(true)
            setShowBadge(true)
        }

        val friendRequestChannel = NotificationChannel(
            CHANNEL_ID_FRIEND_REQUESTS,
            CHANNEL_NAME_FRIEND_REQUESTS,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = CHANNEL_DESCRIPTION_FRIEND_REQUESTS
            enableVibration(true)
            setShowBadge(true)
        }

        notificationManager.createNotificationChannels(
            listOf(messageChannel, friendRequestChannel)
        )
    }

    fun showMessageNotification(
        senderName: String,
        messageText: String,
        conversationId: String? = null
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            conversationId?.let { putExtra("conversationId", it) }
        }

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
            .build()

        notificationManager.notify(notificationId++, notification)
    }

    fun showFriendRequestNotification(fromUserName: String, message: String?) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigateTo", "friend_requests")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (!message.isNullOrBlank()) {
            "$fromUserName: $message"
        } else {
            "$fromUserName wants to be your friend"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_FRIEND_REQUESTS)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("Friend Request")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .build()

        notificationManager.notify(notificationId++, notification)
    }

    fun cancelAll() {
        notificationManager.cancelAll()
    }

    fun cancelNotification(id: Int) {
        notificationManager.cancel(id)
    }
}
