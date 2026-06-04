package com.pinealctx.nexus.client

import com.api.v1.BatchGetUserInfoRequest
import com.api.v1.GetProfileRequest
import com.api.v1.ListDevicesRequest
import com.api.v1.RemoveDeviceRequest
import com.api.v1.ResolveUsernameRequest
import com.api.v1.SetUsernameRequest
import com.api.v1.UpdateProfileRequest
import com.api.v1.User
import com.pinealctx.nexus.core.ContactData
import com.pinealctx.nexus.core.DeviceData
import com.pinealctx.nexus.core.ProfileData
import com.shared.v1.UserInfo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserApi @Inject constructor(
    private val apiClientFactory: ApiClientFactory,
    private val headers: RpcHeaders
) {
    suspend fun getMyProfile(): ProfileData {
        val response = apiClientFactory.createClients()
            .users
            .getProfile(
                request = GetProfileRequest.getDefaultInstance(),
                headers = headers.current()
            )
            .requireMessage()

        return response.profile.toProfileData()
    }

    suspend fun fetchProfile(): ProfileData = getMyProfile()

    suspend fun updateProfile(
        nickname: String? = null,
        signature: String? = null,
        avatarUrl: String? = null
    ): ProfileData {
        val request = UpdateProfileRequest.newBuilder().apply {
            nickname?.let { setNickname(it) }
            signature?.let { setSignature(it) }
            avatarUrl?.let { setAvatarUrl(it) }
        }.build()

        val response = apiClientFactory.createClients()
            .users
            .updateProfile(request, headers.current())
            .requireMessage()

        return response.profile.toProfileData()
    }

    suspend fun setUsername(username: String): ProfileData {
        val response = apiClientFactory.createClients()
            .users
            .setUsername(
                request = SetUsernameRequest.newBuilder()
                    .setUsername(username)
                    .build(),
                headers = headers.current()
            )
            .requireMessage()

        return response.profile.toProfileData()
    }

    suspend fun resolveUsername(username: String): ContactData? {
        val response = apiClientFactory.createClients()
            .users
            .resolveUsername(
                request = ResolveUsernameRequest.newBuilder()
                    .setUsername(username)
                    .build(),
                headers = headers.current()
            )
            .requireMessage()

        return if (response.hasUser()) response.user.toContactData() else null
    }

    suspend fun batchGetUserInfo(userIds: List<Int>): List<ContactData> {
        if (userIds.isEmpty()) return emptyList()
        val response = apiClientFactory.createClients()
            .users
            .batchGetUserInfo(
                request = BatchGetUserInfoRequest.newBuilder()
                    .addAllUserIds(userIds)
                    .build(),
                headers = headers.current()
            )
            .requireMessage()

        return userIds.mapNotNull { userId ->
            response.usersMap[userId]?.toContactData()
        }
    }

    suspend fun listDevices(): List<DeviceData> {
        val response = apiClientFactory.createClients()
            .users
            .listDevices(
                request = ListDevicesRequest.getDefaultInstance(),
                headers = headers.current()
            )
            .requireMessage()

        return response.devicesList.map { device ->
            DeviceData(
                deviceId = device.deviceId,
                deviceType = device.deviceType.number,
                deviceName = device.deviceName,
                deviceModel = device.deviceModel,
                osVersion = device.osVersion,
                appVersion = device.appVersion,
                loginAt = device.loginAt,
                lastActiveAt = device.lastActiveAt,
                isCurrent = device.isCurrent
            )
        }
    }

    suspend fun removeDevice(deviceId: String) {
        apiClientFactory.createClients()
            .users
            .removeDevice(
                request = RemoveDeviceRequest.newBuilder()
                    .setDeviceId(deviceId)
                    .build(),
                headers = headers.current()
            )
            .requireMessage()
    }
}

private fun User.toProfileData(): ProfileData {
    return ProfileData(
        userId = userId,
        username = username,
        nickname = nickname,
        avatarUrl = avatarUrl,
        signature = signature,
        phone = if (hasPhone()) phone else null,
        email = if (hasEmail()) email else null
    )
}

private fun UserInfo.toContactData(): ContactData {
    return ContactData(
        userId = userId,
        username = username,
        nickname = nickname,
        avatarUrl = avatarUrl,
        alias = null
    )
}

