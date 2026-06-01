package com.pinealctx.nexus

import android.app.Application
import com.pinealctx.nexus.core.AppEventBus
import com.pinealctx.nexus.core.SyncManager
import dagger.hilt.android.HiltAndroidApp
import uniffi.nexus_ffi.setEventListener
import javax.inject.Inject

@HiltAndroidApp
class NexusApp : Application() {

    @Inject lateinit var appEventBus: AppEventBus
    @Inject lateinit var syncManager: SyncManager

    override fun onCreate() {
        super.onCreate()
        setEventListener(appEventBus)
        syncManager.initialize()
        if (syncManager.tryRestoreSession()) {
            syncManager.startSession()
        }
    }
}
