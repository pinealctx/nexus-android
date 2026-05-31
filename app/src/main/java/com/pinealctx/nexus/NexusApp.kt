package com.pinealctx.nexus

import android.app.Application
import com.pinealctx.nexus.core.EventBridge
import com.pinealctx.nexus.core.SyncManager
import dagger.hilt.android.HiltAndroidApp
import uniffi.nexus_ffi.setEventListener
import javax.inject.Inject

@HiltAndroidApp
class NexusApp : Application() {

    @Inject lateinit var eventBridge: EventBridge
    @Inject lateinit var syncManager: SyncManager

    override fun onCreate() {
        super.onCreate()
        setEventListener(eventBridge)
        syncManager.initialize()
        if (syncManager.tryRestoreSession()) {
            syncManager.startSession()
        }
    }
}
