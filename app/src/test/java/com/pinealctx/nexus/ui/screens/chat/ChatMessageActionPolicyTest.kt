package com.pinealctx.nexus.ui.screens.chat

import com.pinealctx.nexus.core.MessageContent
import com.pinealctx.nexus.core.MessageData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessageActionPolicyTest {
    @Test
    fun `text messages can be copied replied edited and recalled by sender`() {
        val state = ChatMessageActionPolicy.forMessage(
            message = remote(content = MessageContent.Text("hello"), senderId = 7),
            currentUserId = 7,
            pendingActionMessageId = null
        )

        assertTrue(state.canReply)
        assertTrue(state.canCopy)
        assertTrue(state.canEdit)
        assertTrue(state.canRecall)
        assertTrue(state.canDelete)
        assertEquals("hello", state.copyText)
    }

    @Test
    fun `markdown can be copied but not edited`() {
        val state = ChatMessageActionPolicy.forMessage(
            message = remote(content = MessageContent.Markdown("# hello"), senderId = 7),
            currentUserId = 7,
            pendingActionMessageId = null
        )

        assertTrue(state.canCopy)
        assertFalse(state.canEdit)
        assertEquals("# hello", state.copyText)
    }

    @Test
    fun `recalled messages can only be deleted`() {
        val state = ChatMessageActionPolicy.forMessage(
            message = remote(content = MessageContent.Recalled, senderId = 7, recalled = true),
            currentUserId = 7,
            pendingActionMessageId = null
        )

        assertFalse(state.canReply)
        assertFalse(state.canCopy)
        assertFalse(state.canEdit)
        assertFalse(state.canRecall)
        assertTrue(state.canDelete)
        assertNull(state.copyText)
    }

    @Test
    fun `pending action disables menu commands`() {
        val state = ChatMessageActionPolicy.forMessage(
            message = remote(content = MessageContent.Text("hello"), messageId = 10L, senderId = 7),
            currentUserId = 7,
            pendingActionMessageId = 10L
        )

        assertTrue(state.isPending)
        assertFalse(state.canReply)
        assertFalse(state.canCopy)
        assertFalse(state.canEdit)
        assertFalse(state.canRecall)
        assertFalse(state.canDelete)
    }

    private fun remote(
        content: MessageContent,
        messageId: Long = 10L,
        senderId: Int,
        recalled: Boolean = false
    ): ChatMessageItem.Remote {
        return ChatMessageItem.Remote(
            MessageData(
                conversationId = "100",
                messageId = messageId,
                senderId = senderId,
                content = content,
                replyToMessageId = null,
                replyContext = null,
                createdAt = 1000L,
                edited = false,
                recalled = recalled
            )
        )
    }
}
