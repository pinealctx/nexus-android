package com.pinealctx.nexus.client

import android.content.Context
import com.pinealctx.nexus.core.SecureStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncStateStore @Inject constructor(
    @ApplicationContext context: Context,
    private val secureStorage: SecureStorage
) {
    private val prefs = context.getSharedPreferences("nexus_sync_state", Context.MODE_PRIVATE)

    fun getLocalSn(): Int {
        val userId = secureStorage.getUserId()
        if (userId <= 0) return 0
        return prefs.getInt(localSnKey(userId), 0)
    }

    fun setLocalSn(sn: Int) {
        val userId = secureStorage.getUserId()
        if (userId <= 0) return
        prefs.edit()
            .putInt(localSnKey(userId), sn)
            .putLong(localSnUpdatedAtKey(userId), System.currentTimeMillis())
            .apply()
    }

    fun clearCurrentUser() {
        val userId = secureStorage.getUserId()
        if (userId <= 0) return
        prefs.edit()
            .remove(localSnKey(userId))
            .remove(localSnUpdatedAtKey(userId))
            .apply()
    }

    private fun localSnKey(userId: Int): String = "local_sn_$userId"

    private fun localSnUpdatedAtKey(userId: Int): String = "local_sn_updated_at_$userId"
}

