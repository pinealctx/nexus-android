package com.pinealctx.nexus.core.managers

import com.pinealctx.nexus.core.ContactData
import com.pinealctx.nexus.core.DeviceData
import com.pinealctx.nexus.core.ProfileData
import com.pinealctx.nexus.client.UserApi
import com.pinealctx.nexus.local.LocalDataStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserManager @Inject constructor(
    private val userApi: UserApi,
    private val localDataStore: LocalDataStore
) {
    fun getCachedMyProfile(): ProfileData? = localDataStore.getMyProfile()

    suspend fun getMyProfile(): ProfileData? {
        return localDataStore.getMyProfile()
            ?: userApi.getMyProfile()
                .also { localDataStore.upsertMyProfile(it) }
    }

    suspend fun fetchProfile() {
        userApi.fetchProfile().also { localDataStore.upsertMyProfile(it) }
    }

    suspend fun updateProfile(nickname: String? = null, signature: String? = null, avatarUrl: String? = null) {
        userApi.updateProfile(nickname, signature, avatarUrl)
            .also { localDataStore.upsertMyProfile(it) }
    }

    suspend fun setUsername(username: String) {
        userApi.setUsername(username).also { localDataStore.upsertMyProfile(it) }
    }

    suspend fun resolveUsername(username: String): ContactData? {
        return localDataStore.getUserByUsername(username)
            ?: userApi.resolveUsername(username)
                ?.also { localDataStore.upsertUser(it) }
    }

    suspend fun batchGetUserInfo(userIds: List<Int>): List<ContactData> {
        if (userIds.isEmpty()) return emptyList()
        val cached = localDataStore.getUsers(userIds).associateBy { it.userId }
        val missingIds = userIds.distinct().filterNot { cached.containsKey(it) }
        val fetched = if (missingIds.isEmpty()) {
            emptyList()
        } else {
            userApi.batchGetUserInfo(missingIds)
                .also { localDataStore.upsertUsers(it) }
        }
        val fetchedById = fetched.associateBy { it.userId }
        return userIds.mapNotNull { userId -> cached[userId] ?: fetchedById[userId] }
    }

    suspend fun listDevices(): List<DeviceData> = userApi.listDevices()

    suspend fun removeDevice(deviceId: String) = userApi.removeDevice(deviceId)
}
