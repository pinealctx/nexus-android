package com.pinealctx.nexus.core.managers

import com.pinealctx.nexus.core.ContactData
import com.pinealctx.nexus.core.DeviceData
import com.pinealctx.nexus.core.NexusClientProvider
import com.pinealctx.nexus.core.ProfileData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserManager @Inject constructor(
    private val clientProvider: NexusClientProvider
) {
    fun getMyProfile(): ProfileData? {
        val p = clientProvider.getOrNull()?.getMyProfile() ?: return null
        return ProfileData(p.userId, p.username, p.nickname, p.avatarUrl, p.signature, p.phone, p.email)
    }

    fun fetchProfile() { clientProvider.getOrNull()?.fetchProfile() }

    fun updateProfile(nickname: String? = null, signature: String? = null, avatarUrl: String? = null) {
        clientProvider.getOrNull()?.updateProfile(nickname, signature, avatarUrl)
    }

    fun setUsername(username: String) { clientProvider.get().setUsername(username) }

    fun resolveUsername(username: String): ContactData? {
        val result = clientProvider.getOrNull()?.resolveUsername(username) ?: return null
        return result.toContactData()
    }

    fun batchGetUserInfo(userIds: List<Int>): List<ContactData> {
        val results = clientProvider.getOrNull()?.batchGetUserInfo(userIds) ?: return emptyList()
        return results.map { it.toContactData() }
    }

    fun listDevices(): List<DeviceData> {
        val devices = clientProvider.getOrNull()?.listDevices() ?: return emptyList()
        return devices.map { d ->
            DeviceData(d.deviceId, d.deviceType, d.deviceName, d.deviceModel, d.osVersion, d.appVersion, d.loginAt, d.lastActiveAt, d.isCurrent)
        }
    }

    fun removeDevice(deviceId: String) { clientProvider.getOrNull()?.removeDevice(deviceId) }
}

private fun uniffi.nexus_ffi.ContactInfo.toContactData() = ContactData(
    userId, username, nickname, avatarUrl, alias
)
