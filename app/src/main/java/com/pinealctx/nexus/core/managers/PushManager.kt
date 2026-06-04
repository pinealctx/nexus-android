package com.pinealctx.nexus.core.managers

import com.pinealctx.nexus.client.PushApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking

@Singleton
class PushManager @Inject constructor(
    private val pushApi: PushApi
) {
    fun registerPushToken(token: String, platform: Int) {
        runBlocking { pushApi.registerPushToken(token, platform) }
    }

    fun unregisterPushToken() {
        runBlocking { pushApi.unregisterPushToken() }
    }

    fun clearBadge() {
        runBlocking { pushApi.clearBadge() }
    }
}
