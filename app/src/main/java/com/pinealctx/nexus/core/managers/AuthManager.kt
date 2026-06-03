package com.pinealctx.nexus.core.managers

import com.pinealctx.nexus.core.ClientConfigData
import com.pinealctx.nexus.core.LoginResult
import com.pinealctx.nexus.core.NexusClientProvider
import com.pinealctx.nexus.core.ServerConfigData
import com.pinealctx.nexus.core.VerifyCodeData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthManager @Inject constructor(
    private val clientProvider: NexusClientProvider
) {
    fun getClientConfig(): ClientConfigData {
        val config = clientProvider.get().getClientConfig()
        return ClientConfigData(
            phoneEnabled = config.phoneEnabled,
            emailEnabled = config.emailEnabled,
            wsUrl = config.wsUrl
        )
    }

    fun requestVerifyCode(identityType: Int, identityValue: String): VerifyCodeData {
        val result = clientProvider.get().requestVerifyCode(identityType, identityValue)
        return VerifyCodeData(verifyToken = result.verifyToken, expiresIn = result.expiresIn)
    }

    fun verifyCode(verifyToken: String, code: String): LoginResult {
        val result = clientProvider.get().verifyCode(verifyToken, code)
        return LoginResult(
            userId = result.userId,
            accessToken = result.accessToken,
            refreshToken = result.refreshToken,
            expiresIn = result.expiresIn,
            isNewUser = result.isNewUser
        )
    }

    fun restoreSession(accessToken: String, refreshToken: String, expiresIn: Int, userId: Int) {
        clientProvider.getOrNull()?.restoreSession(accessToken, refreshToken, expiresIn, userId)
    }

    fun reopenForUser(userId: Int) {
        clientProvider.reopenForUser(userId)
    }

    fun getServerConfig(): ServerConfigData = clientProvider.getServerConfig()

    fun setServerApiBaseUrl(apiBaseUrl: String) {
        clientProvider.setServerApiBaseUrl(apiBaseUrl)
    }

    fun applyDiscoveredWsUrl(wsUrl: String?): Boolean = clientProvider.applyDiscoveredWsUrl(wsUrl)

    fun resetServerConfig() {
        clientProvider.resetServerConfig()
    }

    fun isAuthenticated(): Boolean = clientProvider.getOrNull()?.isAuthenticated() ?: false

    fun logout() { clientProvider.getOrNull()?.logout() }

    fun logoutAll() { clientProvider.getOrNull()?.logoutAll() }

    fun setupPassword(password: String) { clientProvider.get().setupPassword(password) }

    fun changePassword(oldPassword: String, newPassword: String) {
        clientProvider.get().changePassword(oldPassword, newPassword)
    }
}
