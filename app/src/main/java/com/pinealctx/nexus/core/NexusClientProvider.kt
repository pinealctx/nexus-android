package com.pinealctx.nexus.core

import android.content.Context
import android.os.Build
import android.util.Log
import com.pinealctx.nexus.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import uniffi.nexus_ffi.CoreConfig
import uniffi.nexus_ffi.DeviceInfo
import uniffi.nexus_ffi.NexusClient
import uniffi.nexus_ffi.databasePathForUser
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NexusClientProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secureStorage: SecureStorage
) {
    private var client: NexusClient? = null
    private var activeUserId: Int? = null

    @Synchronized
    fun initialize(userId: Int? = secureStorage.getUserId().takeIf { it > 0 }) {
        if (client != null) return

        System.loadLibrary("nexus_ffi")
        val serverConfig = resolveServerConfig()

        val dbDir = context.getDatabasePath("nexus.db").parentFile
        dbDir?.mkdirs()
        val dbPath = if (userId != null) {
            databasePathForUser(dbDir?.absolutePath ?: context.filesDir.absolutePath, userId)
        } else {
            context.getDatabasePath("nexus-bootstrap.db").absolutePath
        }

        val config = CoreConfig(
            databasePath = dbPath,
            apiBaseUrl = serverConfig.apiBaseUrl,
            wsUrl = serverConfig.wsUrl,
            deviceId = secureStorage.getDeviceId(),
            deviceInfo = DeviceInfo(
                deviceName = Build.DEVICE,
                deviceModel = Build.MODEL,
                osVersion = "Android ${Build.VERSION.RELEASE}",
                appVersion = BuildConfig.VERSION_NAME
            )
        )

        Log.i(
            "NexusCore",
            "Initializing core api=${serverConfig.apiBaseUrl} ws=${serverConfig.wsUrl} user=${userId ?: "bootstrap"}"
        )
        client = NexusClient(config)
        activeUserId = userId
    }

    @Synchronized
    fun reopenForUser(userId: Int) {
        if (client != null && activeUserId == userId) return
        shutdown()
        initialize(userId)
    }

    @Synchronized
    fun setServerApiBaseUrl(apiBaseUrl: String) {
        val normalizedApiBaseUrl = normalizeApiBaseUrl(apiBaseUrl)
        secureStorage.saveServerConfig(normalizedApiBaseUrl, deriveWsUrl(normalizedApiBaseUrl))
        reopenCurrentUser()
    }

    @Synchronized
    fun resetServerConfig() {
        secureStorage.clearServerConfig()
        reopenCurrentUser()
    }

    @Synchronized
    fun applyDiscoveredWsUrl(wsUrl: String?): Boolean {
        val normalizedWsUrl = normalizeWsUrl(wsUrl?.takeIf { it.isNotBlank() } ?: return false)
        if (normalizedWsUrl == resolveServerConfig().wsUrl) return false
        secureStorage.saveWsUrl(normalizedWsUrl)
        reopenCurrentUser()
        return true
    }

    fun getServerConfig(): ServerConfigData = resolveServerConfig()

    @Synchronized
    fun shutdown() {
        client?.close()
        client = null
        activeUserId = null
    }

    fun get(): NexusClient =
        client ?: throw IllegalStateException("Core not initialized")

    fun getOrNull(): NexusClient? = client

    private fun reopenCurrentUser() {
        val userId = activeUserId
        shutdown()
        initialize(userId ?: secureStorage.getUserId().takeIf { it > 0 })
    }

    private fun resolveServerConfig(): ServerConfigData {
        val savedApiBaseUrl = secureStorage.getApiBaseUrl()?.takeIf { it.isNotBlank() }
        val savedWsUrl = secureStorage.getWsUrl()?.takeIf { it.isNotBlank() }
        val apiBaseUrl = savedApiBaseUrl ?: BuildConfig.NEXUS_API_BASE_URL
        val wsUrl = savedWsUrl ?: if (savedApiBaseUrl != null) {
            deriveWsUrl(apiBaseUrl)
        } else {
            BuildConfig.NEXUS_WS_URL
        }
        return ServerConfigData(
            apiBaseUrl = apiBaseUrl,
            wsUrl = wsUrl,
            defaultApiBaseUrl = BuildConfig.NEXUS_API_BASE_URL,
            defaultWsUrl = BuildConfig.NEXUS_WS_URL,
            isCustom = savedApiBaseUrl != null
        )
    }

    private fun normalizeApiBaseUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim().trimEnd('/')
        val uri = URI(trimmed)
        val scheme = uri.scheme?.lowercase()
        require(scheme == "http" || scheme == "https") {
            "Server address must start with http:// or https://"
        }
        require(!uri.host.isNullOrBlank()) {
            "Server address must include a host"
        }
        return trimmed
    }

    private fun deriveWsUrl(apiBaseUrl: String): String {
        val uri = URI(apiBaseUrl)
        val wsScheme = when (uri.scheme?.lowercase()) {
            "http" -> "ws"
            "https" -> "wss"
            else -> throw IllegalArgumentException("Server address must start with http:// or https://")
        }
        val path = uri.rawPath?.trimEnd('/')?.takeIf { it.isNotBlank() } ?: ""
        return URI(
            wsScheme,
            uri.rawUserInfo,
            uri.host,
            uri.port,
            "$path/ws",
            null,
            null
        ).toString()
    }

    private fun normalizeWsUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim().trimEnd('/')
        val uri = URI(trimmed)
        val scheme = uri.scheme?.lowercase()
        require(scheme == "ws" || scheme == "wss") {
            "Gateway address must start with ws:// or wss://"
        }
        require(!uri.host.isNullOrBlank()) {
            "Gateway address must include a host"
        }
        return trimmed
    }
}
