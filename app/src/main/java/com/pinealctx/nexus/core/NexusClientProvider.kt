package com.pinealctx.nexus.core

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import uniffi.nexus_ffi.CoreConfig
import uniffi.nexus_ffi.DeviceInfo
import uniffi.nexus_ffi.NexusClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NexusClientProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secureStorage: SecureStorage
) {
    private var client: NexusClient? = null

    fun initialize() {
        if (client != null) return

        System.loadLibrary("nexus_ffi")

        val dbPath = context.getDatabasePath("nexus.db").absolutePath
        context.getDatabasePath("nexus.db").parentFile?.mkdirs()

        val config = CoreConfig(
            databasePath = dbPath,
            apiBaseUrl = "https://api.nexus-dev.xsyphon.com",
            wsUrl = "wss://api.nexus-dev.xsyphon.com/ws",
            deviceId = secureStorage.getDeviceId(),
            deviceInfo = DeviceInfo(
                deviceName = Build.DEVICE,
                deviceModel = Build.MODEL,
                osVersion = "Android ${Build.VERSION.RELEASE}",
                appVersion = "0.1.0"
            )
        )

        client = NexusClient(config)
    }

    fun shutdown() {
        client?.close()
        client = null
    }

    fun get(): NexusClient =
        client ?: throw IllegalStateException("Core not initialized")

    fun getOrNull(): NexusClient? = client
}
