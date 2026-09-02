package com.pinealctx.nexus.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemotePushPayloadTest {
    @Test
    fun `parses message payload`() {
        val payload = RemotePushPayload.from(
            mapOf(
                "type" to "message",
                "sender_name" to "Alice",
                "group_name" to "Nexus",
                "message" to "hello",
                "message_type" to "1",
                "conversation_id" to "42"
            )
        )

        assertEquals(RemotePushPayload.Type.MESSAGE, payload.type)
        assertEquals("Alice", payload.senderName)
        assertEquals("Nexus", payload.groupName)
        assertEquals("hello", payload.message)
        assertEquals(1, payload.messageType)
        assertEquals("42", payload.conversationId)
    }

    @Test
    fun `defaults unknown and malformed values safely`() {
        val payload = RemotePushPayload.from(
            mapOf("type" to "other", "message_type" to "not-a-number")
        )

        assertEquals(RemotePushPayload.Type.UNKNOWN, payload.type)
        assertEquals(0, payload.messageType)
        assertNull(payload.message)
        assertNull(payload.conversationId)
    }
}
