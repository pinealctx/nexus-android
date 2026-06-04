package com.pinealctx.nexus.core.managers

import com.pinealctx.nexus.core.ContactData
import com.pinealctx.nexus.core.DeviceData
import com.pinealctx.nexus.core.ProfileData
import com.pinealctx.nexus.client.UserApi
import com.pinealctx.nexus.local.LocalDataStore
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserManager @Inject constructor(
    private val userApi: UserApi,
    private val localDataStore: LocalDataStore
) {
    fun getMyProfile(): ProfileData? {
        return localDataStore.getMyProfile()
            ?: runBlocking {
                userApi.getMyProfile()
                    .also { localDataStore.upsertMyProfile(it) }
            }
    }

    fun fetchProfile() {
        runBlocking {
            userApi.fetchProfile()
                .also { localDataStore.upsertMyProfile(it) }
        }
    }

    fun updateProfile(nickname: String? = null, signature: String? = null, avatarUrl: String? = null) {
        runBlocking {
            userApi.updateProfile(nickname, signature, avatarUrl)
                .also { localDataStore.upsertMyProfile(it) }
        }
    }

    fun setUsername(username: String) {
        runBlocking {
            userApi.setUsername(username)
                .also { localDataStore.upsertMyProfile(it) }
        }
    }

    fun resolveUsername(username: String): ContactData? {
        return localDataStore.getUserByUsername(username)
            ?: runBlocking {
                userApi.resolveUsername(username)
                    ?.also { localDataStore.upsertUser(it) }
            }
    }

    fun batchGetUserInfo(userIds: List<Int>): List<ContactData> {
        if (userIds.isEmpty()) return emptyList()
        val cached = localDataStore.getUsers(userIds).associateBy { it.userId }
        val missingIds = userIds.distinct().filterNot { cached.containsKey(it) }
        val fetched = if (missingIds.isEmpty()) {
            emptyList()
        } else {
            runBlocking { userApi.batchGetUserInfo(missingIds) }
                .also { localDataStore.upsertUsers(it) }
        }
        val fetchedById = fetched.associateBy { it.userId }
        return userIds.mapNotNull { userId -> cached[userId] ?: fetchedById[userId] }
    }

    fun listDevices(): List<DeviceData> {
        return runBlocking { userApi.listDevices() }
    }

    fun removeDevice(deviceId: String) {
        runBlocking { userApi.removeDevice(deviceId) }
    }
}
