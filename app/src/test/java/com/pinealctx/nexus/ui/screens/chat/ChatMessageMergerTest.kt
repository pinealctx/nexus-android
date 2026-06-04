package com.pinealctx.nexus.ui.screens.chat

import com.pinealctx.nexus.core.LocalMessageData
import com.pinealctx.nexus.core.MessageContent
import com.pinealctx.nexus.core.MessageData
import com.pinealctx.nexus.core.MessageReplyContextData
import com.pinealctx.nexus.core.MessageSendState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessageMergerTest {
    @Test
    fun `keeps failed and sending local messages in time order`() {
        val merged = ChatMessageMerger.merge(
            remoteMessages = listOf(remote(id = 10L, createdAt = 100L)),
            localMessages = listOf(
                local(id = 101L, createdAt = 300L, state = MessageSendState.SENDING),
                local(id = 102L, createdAt = 200L, state = MessageSendState.FAILED, replyToMessageId = 10L)
            )
        )

        assertEquals(
            listOf("local:101", "local:102", "remote:100:10"),
            merged.map { it.stableId }
        )
        assertEquals(MessageSendState.SENDING, merged[0].sendState)
        assertEquals(MessageSendState.FAILED, merged[1].sendState)
        assertEquals(10L, merged[1].replyToMessageId)
        assertEquals(ChatReplyPreview(10L, 2, "", "remote-10"), merged[1].replyPreview)
    }

    @Test
    fun `hides local sent echo after matching remote message arrives`() {
        val merged = ChatMessageMerger.merge(
            remoteMessages = listOf(remote(id = 50L, createdAt = 400L)),
            localMessages = listOf(
                local(
                    id = 201L,
                    createdAt = 300L,
                    serverMessageId = 50L,
                    state = MessageSendState.SENT
                )
            )
        )

        assertEquals(listOf("remote:100:50"), merged.map { it.stableId })
        assertTrue(merged.single() is ChatMessageItem.Remote)
    }

    @Test
    fun `exposes remote reply target for the chat UI`() {
        val merged = ChatMessageMerger.merge(
            remoteMessages = listOf(
                remote(id = 51L, createdAt = 500L, replyToMessageId = 50L),
                remote(id = 50L, createdAt = 400L)
            ),
            localMessages = emptyList()
        )

        assertEquals(50L, merged.first().replyToMessageId)
        assertEquals(ChatReplyPreview(50L, 2, "", "remote-50"), merged.first().replyPreview)
    }

    @Test
    fun `uses persisted reply context before loaded fallback preview`() {
        val merged = ChatMessageMerger.merge(
            remoteMessages = listOf(
                remote(
                    id = 51L,
                    createdAt = 500L,
                    replyToMessageId = 50L,
                    replyContext = MessageReplyContextData(50L, 9, "Alice", "persisted")
                ),
                remote(id = 50L, createdAt = 400L)
            ),
            localMessages = emptyList()
        )

        assertEquals(ChatReplyPreview(50L, 9, "Alice", "persisted"), merged.first().replyPreview)
    }

    @Test
    fun `finds only remote messages by server id`() {
        val merged = ChatMessageMerger.merge(
            remoteMessages = listOf(remote(id = 10L, createdAt = 200L)),
            localMessages = listOf(local(id = 10L, createdAt = 300L, state = MessageSendState.SENDING))
        )

        assertEquals(1, ChatMessageIndex.remoteIndexOf(merged, 10L))
        assertEquals(-1, ChatMessageIndex.remoteIndexOf(merged, 999L))
    }

    private fun remote(
        id: Long,
        createdAt: Long,
        replyToMessageId: Long? = null,
        replyContext: MessageReplyContextData? = null
    ): MessageData =
        MessageData(
            conversationId = "100",
            messageId = id,
            senderId = 2,
            content = MessageContent.Text("remote-$id"),
            replyToMessageId = replyToMessageId,
            replyContext = replyContext,
            createdAt = createdAt,
            edited = false,
            recalled = false
        )

    private fun local(
        id: Long,
        createdAt: Long,
        serverMessageId: Long? = null,
        replyToMessageId: Long? = null,
        state: MessageSendState
    ): LocalMessageData =
        LocalMessageData(
            clientMessageId = id,
            conversationId = "100",
            serverMessageId = serverMessageId,
            senderId = 1,
            content = MessageContent.Text("local-$id"),
            replyToMessageId = replyToMessageId,
            createdAt = createdAt,
            sendState = state
        )
}
