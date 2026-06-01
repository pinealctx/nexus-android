package com.pinealctx.nexus.core

import android.content.Context
import android.content.Intent
import com.pinealctx.nexus.core.managers.SyncBridge
import com.pinealctx.nexus.util.NotificationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncBridge: SyncBridge,
    private val sessionManager: SessionManager,
    private val appEventBus: AppEventBus,
    private val notificationHelper: NotificationHelper
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var onForceLogout: (() -> Unit)? = null
    var activeConversationId: String? = null

    fun initialize() {
        observeTokenRefresh()
        observeForceLogout()
        observeColdStartRequired()
        observeMessagesForNotification()
    }

    fun startSession() {
        scope.launch {
            val localSn = syncBridge.getLocalSn()
            if (localSn == 0L) {
                syncBridge.coldStart()
            }
            syncBridge.startSync()
            startForegroundService()
        }
    }

    fun stopSession() {
        syncBridge.stopSync()
        stopForegroundService()
        sessionManager.clearSession()
        syncBridge.clearLocalData()
    }

    private fun observeTokenRefresh() {
        appEventBus.tokenRefreshed()
            .onEach { event ->
                sessionManager.saveTokens(
                    event.accessToken,
                    event.refreshToken,
                    event.expiresIn,
                    sessionManager.getUserId()
                )
            }
            .launchIn(scope)
    }

    private fun observeForceLogout() {
        appEventBus.forceLogout()
            .onEach {
                syncBridge.stopSync()
                stopForegroundService()
                sessionManager.clearSession()
                syncBridge.clearLocalData()
                onForceLogout?.invoke()
            }
            .launchIn(scope)
    }

    private fun observeColdStartRequired() {
        appEventBus.coldStartRequired()
            .onEach {
                syncBridge.clearLocalData()
                syncBridge.coldStart()
            }
            .launchIn(scope)
    }

    private fun observeMessagesForNotification() {
        appEventBus.messagesUpdated()
            .onEach { event ->
                if (event.conversationId != activeConversationId) {
                    notificationHelper.showMessageNotification(
                        senderName = "New Message",
                        messageText = "You have a new message",
                        conversationId = event.conversationId
                    )
                }
            }
            .launchIn(scope)
    }

    private fun startForegroundService() {
        val intent = Intent(context, com.pinealctx.nexus.service.NexusForegroundService::class.java)
        context.startForegroundService(intent)
    }

    private fun stopForegroundService() {
        val intent = Intent(context, com.pinealctx.nexus.service.NexusForegroundService::class.java)
        context.stopService(intent)
    }
}
