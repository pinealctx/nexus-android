package com.pinealctx.nexus.core

import android.util.Log
import com.pinealctx.nexus.core.managers.SyncBridge
import com.pinealctx.nexus.util.NotificationHelper
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
    private val syncBridge: SyncBridge,
    private val sessionManager: SessionManager,
    private val appEventBus: AppEventBus,
    private val notificationHelper: NotificationHelper,
    private val syncScheduler: SyncScheduler,
    private val messageSendScheduler: MessageSendScheduler,
    private val pushTokenRegistrar: PushTokenRegistrar,
    private val sessionBootstrapper: SessionBootstrapper
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var onForceLogout: (() -> Unit)? = null
    var activeConversationId: String?
        get() = notificationHelper.activeConversationId
        set(value) { notificationHelper.activeConversationId = value }

    fun initialize() {
        observeTokenRefresh()
        observeForceLogout()
        observeColdStartRequired()
    }

    fun startSession() {
        scope.launch {
            val localSn = syncBridge.getLocalSn()
            if (localSn == 0L) {
                runColdStart()
            }
            syncBridge.startSync()
            syncScheduler.schedulePeriodicSync()
            messageSendScheduler.reschedulePending()
            pushTokenRegistrar.registerCurrentTokenIfAvailable()
        }
    }

    fun stopSession() {
        notificationHelper.cancelAll()
        syncBridge.stopSync()
        syncScheduler.cancelAll()
        messageSendScheduler.cancelAll()
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
                notificationHelper.cancelAll()
                syncBridge.stopSync()
                syncScheduler.cancelAll()
                messageSendScheduler.cancelAll()
                sessionManager.clearSession()
                syncBridge.clearLocalData()
                onForceLogout?.invoke()
            }
            .launchIn(scope)
    }

    private fun observeColdStartRequired() {
        appEventBus.coldStartRequired()
            .onEach {
                try {
                    syncBridge.resetSyncedData()
                    runColdStart()
                } catch (error: Exception) {
                    Log.w("NexusSync", "Cold-start recovery failed", error)
                    syncScheduler.enqueueImmediateSync()
                }
            }
            .launchIn(scope)
    }

    private suspend fun runColdStart() {
        syncBridge.coldStart()
        sessionBootstrapper.hydrate()
        appEventBus.emitColdStartCompleted()
    }

}
