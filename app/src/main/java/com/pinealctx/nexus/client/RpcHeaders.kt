package com.pinealctx.nexus.client

import com.connectrpc.Headers
import com.pinealctx.nexus.core.SecureStorage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RpcHeaders @Inject constructor(
    private val secureStorage: SecureStorage
) {
    fun current(includeAuth: Boolean = true): Headers {
        val headers = linkedMapOf<String, List<String>>(
            "x-nexus-device-id" to listOf(secureStorage.getDeviceId())
        )

        val accessToken = secureStorage.getAccessToken()
        if (includeAuth && !accessToken.isNullOrBlank()) {
            headers["Authorization"] = listOf("Bearer $accessToken")
        }

        return headers
    }
}
