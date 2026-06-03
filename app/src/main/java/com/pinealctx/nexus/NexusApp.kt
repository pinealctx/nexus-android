package com.pinealctx.nexus

import android.app.Application
import android.util.Log
import com.pinealctx.nexus.core.AppEventBus
import com.pinealctx.nexus.core.NexusClientProvider
import com.pinealctx.nexus.core.SyncManager
import com.pinealctx.nexus.util.LocaleManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uniffi.nexus_ffi.setEventListener
import javax.inject.Inject

@HiltAndroidApp
class NexusApp : Application() {

    @Inject lateinit var appEventBus: AppEventBus
    @Inject lateinit var clientProvider: NexusClientProvider
    @Inject lateinit var syncManager: SyncManager
    @Inject lateinit var localeManager: LocaleManager

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        localeManager.restoreLocale()

        appScope.launch {
            try {
                // Let MainActivity draw its first frame before loading native core and restoring sync.
                delay(750)
                clientProvider.initialize()
                setEventListener(appEventBus)
                syncManager.initialize()
                if (syncManager.tryRestoreSession()) {
                    syncManager.startSession()
                }
            } catch (e: Exception) {
                Log.e("NexusApp", "Failed to initialize core", e)
            }
        }
    }
}
