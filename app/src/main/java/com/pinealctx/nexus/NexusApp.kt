package com.pinealctx.nexus

import android.app.Application
import com.pinealctx.nexus.core.AppEventBus
import com.pinealctx.nexus.core.SyncManager
import com.pinealctx.nexus.util.LocaleManager
import dagger.hilt.android.HiltAndroidApp
import uniffi.nexus_ffi.setEventListener
import javax.inject.Inject

@HiltAndroidApp
class NexusApp : Application() {

    @Inject lateinit var appEventBus: AppEventBus
    @Inject lateinit var syncManager: SyncManager
    @Inject lateinit var localeManager: LocaleManager

    override fun onCreate() {
        super.onCreate()
        localeManager.restoreLocale()
        setEventListener(appEventBus)
        syncManager.initialize()
        if (syncManager.tryRestoreSession()) {
            syncManager.startSession()
        }
    }
}
