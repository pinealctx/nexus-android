package com.pinealctx.nexus.core

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_EXPIRES_IN = "expires_in"
        const val KEY_USER_ID = "user_id"
        const val KEY_SAVED_AT = "saved_at"
        const val KEY_API_BASE_URL = "api_base_url"
        const val KEY_WS_URL = "ws_url"
    }

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "nexus_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveTokens(accessToken: String, refreshToken: String, expiresIn: Int, userId: Int) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putInt(KEY_EXPIRES_IN, expiresIn)
            .putInt(KEY_USER_ID, userId)
            .putLong(KEY_SAVED_AT, System.currentTimeMillis())
            .apply()
    }

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)
    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)
    fun getExpiresIn(): Int = prefs.getInt(KEY_EXPIRES_IN, 0)
    fun getRemainingExpiresIn(nowMs: Long = System.currentTimeMillis()): Int {
        val expiresIn = getExpiresIn()
        if (expiresIn <= 0) return 0
        val savedAt = prefs.getLong(KEY_SAVED_AT, 0L)
        if (savedAt <= 0L) return 0
        val elapsedSeconds = ((nowMs - savedAt).coerceAtLeast(0L) / 1000L).toInt()
        return (expiresIn - elapsedSeconds).coerceAtLeast(0)
    }
    fun getUserId(): Int = prefs.getInt(KEY_USER_ID, 0)
    fun hasTokens(): Boolean = getAccessToken() != null

    fun saveServerConfig(apiBaseUrl: String, wsUrl: String) {
        prefs.edit()
            .putString(KEY_API_BASE_URL, apiBaseUrl)
            .putString(KEY_WS_URL, wsUrl)
            .apply()
    }

    fun saveWsUrl(wsUrl: String) {
        prefs.edit()
            .putString(KEY_WS_URL, wsUrl)
            .apply()
    }

    fun getApiBaseUrl(): String? = prefs.getString(KEY_API_BASE_URL, null)

    fun getWsUrl(): String? = prefs.getString(KEY_WS_URL, null)

    fun clearServerConfig() {
        prefs.edit()
            .remove(KEY_API_BASE_URL)
            .remove(KEY_WS_URL)
            .apply()
    }

    fun getDeviceId(): String {
        return android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        )
    }

    fun clearTokens() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_EXPIRES_IN)
            .remove(KEY_USER_ID)
            .remove(KEY_SAVED_AT)
            .apply()
    }
}
