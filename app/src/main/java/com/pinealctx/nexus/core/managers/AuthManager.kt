package com.pinealctx.nexus.core.managers

import com.pinealctx.nexus.BuildConfig
import com.pinealctx.nexus.core.ClientConfigData
import com.pinealctx.nexus.core.EndpointUrls
import com.pinealctx.nexus.core.LoginResult
import com.pinealctx.nexus.core.SecureStorage
import com.pinealctx.nexus.core.ServerConfigData
import com.pinealctx.nexus.core.VerifyCodeData
import com.pinealctx.nexus.client.AuthApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthManager @Inject constructor(
    private val authApi: AuthApi,
    private val secureStorage: SecureStorage
) {
    suspend fun getClientConfig(): ClientConfigData = authApi.getClientConfig()

    suspend fun requestVerifyCode(identityType: Int, identityValue: String): VerifyCodeData =
        authApi.requestVerifyCode(identityType, identityValue)

    suspend fun verifyCode(verifyToken: String, code: String): LoginResult =
        authApi.verifyCode(verifyToken, code)

    suspend fun loginPassword(identityType: Int, identityValue: String, password: String): LoginResult =
        authApi.loginPassword(identityType, identityValue, password)

    suspend fun refreshAccessToken(): Boolean = authApi.refreshAccessToken()

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

    suspend fun logout() = authApi.logout()

    suspend fun logoutAll() = authApi.logoutAll()

    suspend fun setupPassword(password: String) = authApi.setupPassword(password)

    suspend fun changePassword(oldPassword: String, newPassword: String) =
        authApi.changePassword(oldPassword, newPassword)
}
