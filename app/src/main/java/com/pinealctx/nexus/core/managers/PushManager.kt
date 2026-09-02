package com.pinealctx.nexus.core.managers

import com.pinealctx.nexus.client.PushApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PushManager @Inject constructor(
    private val pushApi: PushApi
) {
    suspend fun registerPushToken(token: String, platform: Int) =
        pushApi.registerPushToken(token, platform)

    suspend fun unregisterPushToken() = pushApi.unregisterPushToken()

    suspend fun clearBadge() = pushApi.clearBadge()
}
