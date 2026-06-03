package com.pinealctx.nexus.core

import android.util.Log
import com.pinealctx.nexus.core.managers.AuthManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor(
    private val authManager: AuthManager,
    private val secureStorage: SecureStorage
) {
    fun tryRestoreSession(): Boolean {
        if (!secureStorage.hasTokens()) return false
        val accessToken = secureStorage.getAccessToken() ?: return false
        val refreshToken = secureStorage.getRefreshToken() ?: return false
        val expiresIn = secureStorage.getRemainingExpiresIn()
        val userId = secureStorage.getUserId()
        if (userId <= 0) return false
        try {
            authManager.reopenForUser(userId)
            authManager.restoreSession(accessToken, refreshToken, expiresIn, userId)
        } catch (e: Exception) {
            Log.w("NexusCore", "Failed to restore saved session", e)
            clearSession()
            return false
        }
        try {
            val config = authManager.getClientConfig()
            if (authManager.applyDiscoveredWsUrl(config.wsUrl)) {
                authManager.restoreSession(accessToken, refreshToken, expiresIn, userId)
            }
        } catch (e: Exception) {
            Log.w("NexusCore", "Failed to refresh client config during session restore", e)
        }
        return true
    }

    fun saveTokens(accessToken: String, refreshToken: String, expiresIn: Int, userId: Int) {
        secureStorage.saveTokens(accessToken, refreshToken, expiresIn, userId)
    }

    fun clearSession() {
        secureStorage.clearTokens()
    }

    fun getUserId(): Int = secureStorage.getUserId()
}
