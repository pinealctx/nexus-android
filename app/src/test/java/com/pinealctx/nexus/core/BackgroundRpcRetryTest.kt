package com.pinealctx.nexus.core

import com.connectrpc.Code
import com.connectrpc.ConnectException
import java.net.SocketException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundRpcRetryTest {
    @Test
    fun unavailableIsRetryable() {
        assertTrue(ConnectException(Code.UNAVAILABLE, "unavailable", null, emptyMap()).isRetryableBackgroundRpcError())
    }

    @Test
    fun nestedSocketFailureIsRetryable() {
        val error = ConnectException(
            Code.UNKNOWN,
            "socket closed",
            SocketException("Socket closed"),
            emptyMap()
        )

        assertTrue(error.isRetryableBackgroundRpcError())
    }

    @Test
    fun authenticationFailureIsNotRetryable() {
        assertFalse(
            ConnectException(Code.UNAUTHENTICATED, "expired", null, emptyMap())
                .isRetryableBackgroundRpcError()
        )
    }
}
