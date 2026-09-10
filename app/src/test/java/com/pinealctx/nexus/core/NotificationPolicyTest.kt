package com.pinealctx.nexus.core

import com.shared.v1.*
import org.junit.Assert.*
import org.junit.Test

class NotificationPolicyTest {
    private val message = MessageEnvelope.newBuilder().setSenderId(2).setBody(MessageBody.newBuilder().setType(MessageType.MESSAGE_TYPE_TEXT)
        .setText(TextContent.newBuilder().setText("hello"))).build()
    @Test fun `only eligible new messages alert`() {
        assertTrue(NotificationPolicy.shouldAlert(message, 1, false))
        assertFalse(NotificationPolicy.shouldAlert(message, 2, false))
        assertFalse(NotificationPolicy.shouldAlert(message, 1, true))
        assertFalse(NotificationPolicy.shouldAlert(message.toBuilder().setEdited(true).build(), 1, false))
        for (type in listOf(MessageType.MESSAGE_TYPE_RECALLED, MessageType.MESSAGE_TYPE_GROUP, MessageType.MESSAGE_TYPE_STREAM)) {
            assertFalse(NotificationPolicy.shouldAlert(message.toBuilder().setBody(MessageBody.newBuilder().setType(type)).build(), 1, false))
        }
    }
    @Test fun `explicit mentions override mute only for their recipient`() {
        val body = message.body.toBuilder().setText(message.body.text.toBuilder().addEntities(MessageEntity.newBuilder().setMention(MentionEntity.newBuilder().setUserId(1))))
        val mentioned = message.toBuilder().setBody(body).build()
        assertTrue(NotificationPolicy.shouldAlert(mentioned, 1, true))
        assertFalse(NotificationPolicy.shouldAlert(mentioned, 3, true))
    }
    @Test fun `markdown notification preview is readable plain text`() {
        assertEquals("Hello link", NotificationPolicy.plainPreview("**Hello** [link](https://example.org)"))
    }
}
