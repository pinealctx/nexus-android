package com.pinealctx.nexus.client

import com.pinealctx.nexus.core.MessageContent
import com.shared.v1.ConversationInfo
import com.shared.v1.ConversationType
import com.shared.v1.FileContent
import com.shared.v1.GroupInfo
import com.shared.v1.MessageBody
import com.shared.v1.MessageEnvelope
import com.shared.v1.MessageType
import com.shared.v1.RecalledContent
import com.shared.v1.ReplyContext
import com.shared.v1.TextContent
import com.shared.v1.UserInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelMappersTest {

    @Test
    fun `maps private conversation with user display name and message preview`() {
        val conversation = ConversationInfo.newBuilder()
            .setConversationId(2001L)
            .setType(ConversationType.CONVERSATION_TYPE_PRIVATE)
            .setPeerId(42)
            .setLastMessageId(8L)
            .setLastMessageTime(123_000L)
            .setLastReadMessageId(5L)
            .build()
        val user = UserInfo.newBuilder()
            .setUserId(42)
            .setUsername("alice")
            .setNickname("Alice")
            .setAvatarUrl("https://example.com/a.png")
            .build()
        val lastMessage = textEnvelope(conversationId = 2001L, messageId = 8L, text = "hello")

        val data = conversation.toConversationData(user = user, lastMessage = lastMessage)

        assertEquals("2001", data.conversationId)
        assertEquals(ConversationType.CONVERSATION_TYPE_PRIVATE.number, data.conversationType)
        assertEquals(42, data.peerId)
        assertEquals("Alice", data.displayName)
        assertEquals("https://example.com/a.png", data.avatarUrl)
        assertEquals("hello", data.lastMessageContent)
        assertEquals(3L, data.unreadCount)
    }

    @Test
    fun `maps group conversation with group display name`() {
        val conversation = ConversationInfo.newBuilder()
            .setConversationId(77L)
            .setType(ConversationType.CONVERSATION_TYPE_GROUP)
            .setPeerId(77)
            .build()
        val group = GroupInfo.newBuilder()
            .setGroupId(77)
            .setName("Core Team")
            .setAvatarUrl("https://example.com/g.png")
            .build()

        val data = conversation.toConversationData(group = group)

        assertEquals("Core Team", data.displayName)
        assertEquals("https://example.com/g.png", data.avatarUrl)
        assertNull(data.lastMessageContent)
    }

    @Test
    fun `maps message envelope body and reply context`() {
        val envelope = textEnvelope(conversationId = 90L, messageId = 12L, text = "reply")
            .toBuilder()
            .setReplyTo(
                ReplyContext.newBuilder()
                    .setMessageId(11L)
                    .setSenderId(7)
                    .setSenderNickname("Bob")
                    .setContentPreview("previous")
            )
            .setEdited(true)
            .build()

        val data = envelope.toMessageData()

        assertEquals("90", data.conversationId)
        assertEquals(12L, data.messageId)
        assertEquals(1001, data.senderId)
        assertEquals(MessageContent.Text("reply"), data.content)
        assertEquals(11L, data.replyToMessageId)
        assertTrue(data.edited)
    }

    @Test
    fun `maps file preview and recalled state`() {
        val fileBody = MessageBody.newBuilder()
            .setType(MessageType.MESSAGE_TYPE_FILE)
            .setFile(
                FileContent.newBuilder()
                    .setFileId("file-1")
                    .setFilename("report.pdf")
                    .setSizeBytes(2048L)
                    .setMimeType("application/pdf")
            )
            .build()
        assertEquals("[File] report.pdf", fileBody.previewText())

        val recalled = MessageEnvelope.newBuilder()
            .setConversationId(1L)
            .setMessageId(2L)
            .setSenderId(3)
            .setCreatedAt(4L)
            .setBody(
                MessageBody.newBuilder()
                    .setType(MessageType.MESSAGE_TYPE_RECALLED)
                    .setRecalled(RecalledContent.newBuilder())
            )
            .build()
            .toMessageData()

        assertEquals(MessageContent.Recalled, recalled.content)
        assertTrue(recalled.recalled)
    }

    private fun textEnvelope(conversationId: Long, messageId: Long, text: String): MessageEnvelope =
        MessageEnvelope.newBuilder()
            .setConversationId(conversationId)
            .setMessageId(messageId)
            .setSenderId(1001)
            .setCreatedAt(456_000L)
            .setBody(
                MessageBody.newBuilder()
                    .setType(MessageType.MESSAGE_TYPE_TEXT)
                    .setText(TextContent.newBuilder().setText(text))
            )
            .build()
}
