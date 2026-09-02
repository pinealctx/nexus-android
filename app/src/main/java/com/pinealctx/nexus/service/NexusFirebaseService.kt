package com.pinealctx.nexus.service

import android.util.Log
import com.pinealctx.nexus.BuildConfig
import com.pinealctx.nexus.core.RemotePushPayload
import com.pinealctx.nexus.core.SecureStorage
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.pinealctx.nexus.core.SyncScheduler
import com.pinealctx.nexus.core.managers.PushManager
import com.pinealctx.nexus.util.NotificationHelper
import com.shared.v1.PushPlatform
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@AndroidEntryPoint
class NexusFirebaseService : FirebaseMessagingService() {
    @Inject lateinit var pushManager: PushManager
    @Inject lateinit var syncScheduler: SyncScheduler
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var secureStorage: SecureStorage

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onRegistered(installationId: String) {
        if (BuildConfig.DEBUG) {
            Log.i(TAG, "FCM registered FID ending in ${installationId.takeLast(6)}")
        }
        if (!secureStorage.hasTokens()) return
        scope.launch {
            runCatching {
                pushManager.registerPushToken(
                    installationId,
                    PushPlatform.PUSH_PLATFORM_FCM.number
                )
            }.onFailure { error -> Log.w(TAG, "Failed to register FID with Nexus", error) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val payload = RemotePushPayload.from(message.data)
        if (payload.type == RemotePushPayload.Type.MESSAGE) {
            payload.conversationId?.let(notificationHelper::markRemoteMessage)
        }
        syncScheduler.enqueueImmediateSync()
        scope.launch {
            when (payload.type) {
                RemotePushPayload.Type.FRIEND_REQUEST -> {
                    notificationHelper.showFriendRequestNotification(
                        fromUserName = payload.senderName.ifBlank {
                            message.notification?.title ?: getString(com.pinealctx.nexus.R.string.app_name)
                        },
                        message = payload.message ?: message.notification?.body
                    )
                }
                RemotePushPayload.Type.MESSAGE -> {
                    val preview = notificationHelper.messagePreview(
                        payload.message ?: message.notification?.body,
                        payload.messageType
                    )
                    val senderName = payload.senderName.ifBlank {
                        message.notification?.title ?: getString(com.pinealctx.nexus.R.string.notification_new_message)
                    }
                    val title = payload.groupName.ifBlank { senderName }
                    val body = if (payload.groupName.isNotBlank()) "$senderName: $preview" else preview
                    notificationHelper.showMessageNotification(
                        senderName = title,
                        messageText = body,
                        conversationId = payload.conversationId
                    )
                }
                RemotePushPayload.Type.SYNC -> Unit
                RemotePushPayload.Type.UNKNOWN -> {
                    val title = message.notification?.title ?: return@launch
                    notificationHelper.showMessageNotification(
                        senderName = title,
                        messageText = message.notification?.body
                            ?: getString(com.pinealctx.nexus.R.string.notification_new_message_body)
                    )
                }
            }
        }
    }

    private companion object {
        const val TAG = "NexusPush"
    }
}
