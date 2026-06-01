package com.pinealctx.nexus.core.managers

import com.pinealctx.nexus.core.NexusClientProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PushManager @Inject constructor(
    private val clientProvider: NexusClientProvider
) {
    fun registerPushToken(token: String, platform: Int) {
        clientProvider.getOrNull()?.registerPushToken(token, platform)
    }

    fun unregisterPushToken() { clientProvider.getOrNull()?.unregisterPushToken() }

    fun clearBadge() { clientProvider.getOrNull()?.clearBadge() }
}
