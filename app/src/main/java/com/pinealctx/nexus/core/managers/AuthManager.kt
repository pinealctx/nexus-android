package com.pinealctx.nexus.core.managers

import com.pinealctx.nexus.BuildConfig
import com.pinealctx.nexus.core.ClientConfigData
import com.pinealctx.nexus.core.EndpointUrls
import com.pinealctx.nexus.core.LoginResult
import com.pinealctx.nexus.core.SecureStorage
import com.pinealctx.nexus.core.ServerConfigData
import com.pinealctx.nexus.core.VerifyCodeData
import com.pinealctx.nexus.client.AuthApi
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthManager @Inject constructor(
    private val authApi: AuthApi,
    private val secureStorage: SecureStorage
) {
    fun getClientConfig(): ClientConfigData {
        return runBlocking { authApi.getClientConfig() }
    }

    fun requestVerifyCode(identityType: Int, identityValue: String): VerifyCodeData {
        return runBlocking { authApi.requestVerifyCode(identityType, identityValue) }
    }

    fun verifyCode(verifyToken: String, code: String): LoginResult {
        return runBlocking { authApi.verifyCode(verifyToken, code) }
    }

    fun loginPassword(identityType: Int, identityValue: String, password: String): LoginResult {
        return runBlocking { authApi.loginPassword(identityType, identityValue, password) }
    }

    fun refreshAccessToken(): Boolean {
        return runBlocking { authApi.refreshAccessToken() }
    }

    fun restoreSession(accessToken: String, refreshToken: String, expiresIn: Int, userId: Int) {
        secureStorage.saveTokens(accessToken, refreshToken, expiresIn, userId)
    }

    fun reopenForUser(userId: Int) {
        // No-op in the generated protocol client path; per-user sync state is keyed elsewhere.
    }

    fun getServerConfig(): ServerConfigData {
        val savedApiBaseUrl = secureStorage.getApiBaseUrl()?.takeIf { it.isNotBlank() }
        val savedWsUrl = secureStorage.getWsUrl()?.takeIf { it.isNotBlank() }
        val apiBaseUrl = savedApiBaseUrl ?: BuildConfig.NEXUS_API_BASE_URL
        val wsUrl = savedWsUrl ?: if (savedApiBaseUrl != null) {
            EndpointUrls.deriveWsUrl(apiBaseUrl)
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

    fun setServerApiBaseUrl(apiBaseUrl: String) {
        val normalizedApiBaseUrl = EndpointUrls.normalizeApiBaseUrl(apiBaseUrl)
        secureStorage.saveServerConfig(normalizedApiBaseUrl, EndpointUrls.deriveWsUrl(normalizedApiBaseUrl))
    }

    fun applyDiscoveredWsUrl(wsUrl: String?): Boolean {
        val normalizedWsUrl = EndpointUrls.normalizeWsUrl(wsUrl?.takeIf { it.isNotBlank() } ?: return false)
        if (normalizedWsUrl == getServerConfig().wsUrl) return false
        secureStorage.saveWsUrl(normalizedWsUrl)
        return true
    }

    fun resetServerConfig() {
        secureStorage.clearServerConfig()
    }

    fun isAuthenticated(): Boolean = secureStorage.hasTokens() && secureStorage.getUserId() > 0

    fun logout() { runBlocking { authApi.logout() } }

    fun logoutAll() { runBlocking { authApi.logoutAll() } }

    fun setupPassword(password: String) { runBlocking { authApi.setupPassword(password) } }

    fun changePassword(oldPassword: String, newPassword: String) {
        runBlocking { authApi.changePassword(oldPassword, newPassword) }
    }
}
