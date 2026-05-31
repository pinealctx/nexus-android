package com.pinealctx.nexus.core

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import com.pinealctx.nexus.util.NotificationHelper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val core: NexusCoreWrapper,
    private val eventBridge: EventBridge,
    private val secureStorage: SecureStorage,
    private val notificationHelper: NotificationHelper
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var onForceLogout: (() -> Unit)? = null
    var activeConversationId: String? = null

    private val drafts = mutableMapOf<String, String>()

    fun saveDraft(conversationId: String, text: String) {
        if (text.isBlank()) {
            drafts.remove(conversationId)
        } else {
            drafts[conversationId] = text
        }
    }

    fun getDraft(conversationId: String): String {
        return drafts[conversationId] ?: ""
    }

    fun clearDraft(conversationId: String) {
        drafts.remove(conversationId)
    }

    fun initialize() {
        observeTokenRefresh()
        observeForceLogout()
        observeColdStartRequired()
        observeMessagesForNotification()
    }

    fun tryRestoreSession(): Boolean {
        if (!secureStorage.hasTokens()) return false
        val accessToken = secureStorage.getAccessToken() ?: return false
        val refreshToken = secureStorage.getRefreshToken() ?: return false
        val expiresIn = secureStorage.getExpiresIn()
        val userId = secureStorage.getUserId()
        core.restoreSession(accessToken, refreshToken, expiresIn, userId)
        return true
    }

    fun startSession() {
        scope.launch {
            val localSn = core.getLocalSn()
            if (localSn == 0L) {
                core.coldStart()
            }
            core.startSync()
            startForegroundService()
        }
    }

    fun stopSession() {
        core.stopSync()
        stopForegroundService()
        secureStorage.clearTokens()
        core.clearLocalData()
    }

    private fun observeTokenRefresh() {
        eventBridge.tokenRefreshed
            .onEach { event ->
                secureStorage.saveTokens(
                    event.accessToken,
                    event.refreshToken,
                    event.expiresIn,
                    secureStorage.getUserId()
                )
            }
            .launchIn(scope)
    }

    private fun observeForceLogout() {
        eventBridge.forceLogout
            .onEach {
                core.stopSync()
                stopForegroundService()
                secureStorage.clearTokens()
                core.clearLocalData()
                onForceLogout?.invoke()
            }
            .launchIn(scope)
    }

    private fun observeColdStartRequired() {
        eventBridge.coldStartRequired
            .onEach {
                core.clearLocalData()
                core.coldStart()
            }
            .launchIn(scope)
    }

    private fun observeMessagesForNotification() {
        eventBridge.messagesUpdated
            .onEach { conversationId ->
                if (conversationId != activeConversationId) {
                    notificationHelper.showMessageNotification(
                        senderName = "New Message",
                        messageText = "You have a new message",
                        conversationId = conversationId
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
