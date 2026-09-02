package com.pinealctx.nexus.client

import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Test

class ApiClientFactoryTest {
    @Test
    fun mediaCallsHaveAnUploadFriendlyDeadline() {
        assertEquals(120.seconds, rpcTimeoutForPath("/api.v1.MediaService/UploadFile"))
        assertEquals(120.seconds, rpcTimeoutForPath("/api.v1.MediaService/UploadChunk"))
    }

    @Test
    fun regularCallsRetainABoundedDeadline() {
        assertEquals(30.seconds, rpcTimeoutForPath("/api.v1.MessageService/ListMessages"))
    }
}
