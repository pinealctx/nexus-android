package com.pinealctx.nexus.core

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import uniffi.nexus_ffi.CoreConfig
import uniffi.nexus_ffi.DeviceInfo
import uniffi.nexus_ffi.NexusClient
import uniffi.nexus_ffi.databasePathForUser
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NexusClientProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secureStorage: SecureStorage
) {
    private var client: NexusClient? = null
    private var activeUserId: Int? = null

    fun initialize(userId: Int? = secureStorage.getUserId().takeIf { it > 0 }) {
        if (client != null) return

        System.loadLibrary("nexus_ffi")

        val dbDir = context.getDatabasePath("nexus.db").parentFile
        dbDir?.mkdirs()
        val dbPath = if (userId != null) {
            databasePathForUser(dbDir?.absolutePath ?: context.filesDir.absolutePath, userId)
        } else {
            context.getDatabasePath("nexus-bootstrap.db").absolutePath
        }

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
        activeUserId = userId
    }

    fun reopenForUser(userId: Int) {
        if (client != null && activeUserId == userId) return
        shutdown()
        initialize(userId)
    }

    fun shutdown() {
        client?.close()
        client = null
        activeUserId = null
    }

    fun get(): NexusClient =
        client ?: throw IllegalStateException("Core not initialized")

    fun getOrNull(): NexusClient? = client
}
