package com.pinealctx.nexus.core

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
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
        const val KEY_CACHE_OWNER_USER_ID = "cache_owner_user_id"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_ALIAS = "nexus_secure_storage_key"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    }

    private val prefs by lazy {
        context.getSharedPreferences("nexus_secure_store_v2", Context.MODE_PRIVATE)
    }

    private val cacheBindingPrefs by lazy {
        context.getSharedPreferences("nexus_cache_binding", Context.MODE_PRIVATE)
    }

    fun saveTokens(accessToken: String, refreshToken: String, expiresIn: Int, userId: Int) {
        if (!hasTokens() || getUserId() != userId) {
            prefs.edit().putString("notification_session", UUID.randomUUID().toString()).apply()
        }
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, encrypt(accessToken))
            .putString(KEY_REFRESH_TOKEN, encrypt(refreshToken))
            .putString(KEY_EXPIRES_IN, encrypt(expiresIn.toString()))
            .putString(KEY_USER_ID, encrypt(userId.toString()))
            .putString(KEY_SAVED_AT, encrypt(System.currentTimeMillis().toString()))
            .apply()
    }

    fun getAccessToken(): String? = readEncrypted(KEY_ACCESS_TOKEN)
    fun getRefreshToken(): String? = readEncrypted(KEY_REFRESH_TOKEN)
    fun getExpiresIn(): Int = readEncrypted(KEY_EXPIRES_IN)?.toIntOrNull() ?: 0
    fun getRemainingExpiresIn(nowMs: Long = System.currentTimeMillis()): Int {
        val expiresIn = getExpiresIn()
        if (expiresIn <= 0) return 0
        val savedAt = readEncrypted(KEY_SAVED_AT)?.toLongOrNull() ?: 0L
        if (savedAt <= 0L) return 0
        val elapsedSeconds = ((nowMs - savedAt).coerceAtLeast(0L) / 1000L).toInt()
        return (expiresIn - elapsedSeconds).coerceAtLeast(0)
    }
    fun getUserId(): Int = readEncrypted(KEY_USER_ID)?.toIntOrNull() ?: 0
    fun hasTokens(): Boolean = getAccessToken() != null
    @Synchronized
    fun notificationSession(): String {
        if (!hasTokens()) return ""
        return prefs.getString("notification_session", null) ?: UUID.randomUUID().toString().also {
            check(prefs.edit().putString("notification_session", it).commit())
        }
    }

    fun getCacheOwnerUserId(): Int = cacheBindingPrefs.getInt(KEY_CACHE_OWNER_USER_ID, 0)

    fun setCacheOwnerUserId(userId: Int) {
        cacheBindingPrefs.edit().putInt(KEY_CACHE_OWNER_USER_ID, userId).apply()
    }

    fun clearCacheOwner() {
        cacheBindingPrefs.edit().remove(KEY_CACHE_OWNER_USER_ID).apply()
    }

    fun saveServerConfig(apiBaseUrl: String, wsUrl: String) {
        prefs.edit()
            .putString(KEY_API_BASE_URL, encrypt(apiBaseUrl))
            .putString(KEY_WS_URL, encrypt(wsUrl))
            .apply()
    }

    fun saveWsUrl(wsUrl: String) {
        prefs.edit()
            .putString(KEY_WS_URL, encrypt(wsUrl))
            .apply()
    }

    fun getApiBaseUrl(): String? = readEncrypted(KEY_API_BASE_URL)

    fun getWsUrl(): String? = readEncrypted(KEY_WS_URL)

    fun clearServerConfig() {
        prefs.edit()
            .remove(KEY_API_BASE_URL)
            .remove(KEY_WS_URL)
            .apply()
    }

    @Synchronized
    fun getDeviceId(): String {
        readEncrypted(KEY_DEVICE_ID)?.let { return it }
        return UUID.randomUUID().toString().also { deviceId ->
            prefs.edit().putString(KEY_DEVICE_ID, encrypt(deviceId)).apply()
        }
    }

    fun clearTokens() {
        prefs.edit()
            .remove("notification_session")
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_EXPIRES_IN)
            .remove(KEY_USER_ID)
            .remove(KEY_SAVED_AT)
            .apply()
    }

    private fun readEncrypted(key: String): String? {
        val encryptedValue = prefs.getString(key, null) ?: return null
        return runCatching { decrypt(encryptedValue) }
            .onFailure { prefs.edit().remove(key).apply() }
            .getOrNull()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val ciphertext = Base64.encodeToString(
            cipher.doFinal(value.toByteArray(Charsets.UTF_8)),
            Base64.NO_WRAP
        )
        return "$iv:$ciphertext"
    }

    private fun decrypt(value: String): String {
        val separator = value.indexOf(':')
        require(separator > 0 && separator < value.lastIndex) { "Invalid encrypted value" }
        val iv = Base64.decode(value.substring(0, separator), Base64.NO_WRAP)
        val ciphertext = Base64.decode(value.substring(separator + 1), Base64.NO_WRAP)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return keyGenerator.generateKey()
    }
}
