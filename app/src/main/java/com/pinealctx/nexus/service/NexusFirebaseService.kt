package com.pinealctx.nexus.service

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.pinealctx.nexus.core.SecureStorage
import com.pinealctx.nexus.core.SyncScheduler
import com.pinealctx.nexus.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class NexusFirebaseService : FirebaseMessagingService() {
    @Inject lateinit var notifications: NotificationHelper
    @Inject lateinit var secureStorage: SecureStorage
    @Inject lateinit var syncScheduler: SyncScheduler

    override fun onRegistered(installationId: String) {
        if (secureStorage.hasTokens()) notifications.enqueue(mapOf("type" to "register", "registration" to installationId))
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (!secureStorage.hasTokens() || data["recipient_id"]?.toIntOrNull() != secureStorage.getUserId() ||
            data["device_id"] != secureStorage.getDeviceId()) return
        notifications.enqueue(data)
        syncScheduler.enqueueImmediateSync()
    }
}
