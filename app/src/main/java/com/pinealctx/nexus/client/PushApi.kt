package com.pinealctx.nexus.client

import com.api.v1.ClearBadgeRequest
import com.api.v1.RegisterTokenRequest
import com.api.v1.UnregisterTokenRequest
import com.shared.v1.PushPlatform
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PushApi @Inject constructor(
    private val apiClientFactory: ApiClientFactory,
    private val headers: RpcHeaders
) {
    suspend fun registerPushToken(token: String, platform: Int) {
        apiClientFactory.createClients()
            .push
            .registerToken(
                request = RegisterTokenRequest.newBuilder()
                    .setDeviceId(apiClientFactory.currentConfig().deviceId)
                    .setToken(token)
                    .setPlatform(platform.toPushPlatform())
                    .build(),
                headers = headers.current()
            )
            .requireMessage()
    }

    suspend fun unregisterPushToken() {
        apiClientFactory.createClients()
            .push
            .unregisterToken(
                request = UnregisterTokenRequest.newBuilder()
                    .setDeviceId(apiClientFactory.currentConfig().deviceId)
                    .build(),
                headers = headers.current()
            )
            .requireMessage()
    }

    suspend fun clearBadge() {
        apiClientFactory.createClients()
            .push
            .clearBadge(ClearBadgeRequest.getDefaultInstance(), headers.current())
            .requireMessage()
    }
}

private fun Int.toPushPlatform(): PushPlatform =
    when (this) {
        PushPlatform.PUSH_PLATFORM_APNS.number -> PushPlatform.PUSH_PLATFORM_APNS
        PushPlatform.PUSH_PLATFORM_FCM.number -> PushPlatform.PUSH_PLATFORM_FCM
        else -> PushPlatform.PUSH_PLATFORM_UNSPECIFIED
    }
