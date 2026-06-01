package com.pinealctx.nexus.core.managers

import com.pinealctx.nexus.core.MessageData
import com.pinealctx.nexus.core.MessageSearchResultData
import com.pinealctx.nexus.core.NexusClientProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageManager @Inject constructor(
    private val clientProvider: NexusClientProvider
) {
    fun getMessages(conversationId: String, limit: Int = 50, beforeId: Long? = null): List<MessageData> {
        val messages = clientProvider.getOrNull()?.getMessages(conversationId, limit, beforeId) ?: return emptyList()
        return messages.map { m ->
            MessageData(
                conversationId = m.conversationId,
                messageId = m.messageId,
                senderId = m.senderId,
                content = m.content,
                replyToMessageId = m.replyToMessageId,
                createdAt = m.createdAt,
                edited = m.edited,
                recalled = m.recalled
            )
        }
    }

    fun sendMessage(conversationId: Long, text: String): Long =
        clientProvider.get().sendMessage(conversationId, text)

    fun sendReplyMessage(conversationId: Long, text: String, replyToMessageId: Long): Long =
        clientProvider.get().sendReplyMessage(conversationId, text, replyToMessageId)

    fun sendImageMessage(conversationId: Long, fileId: String, width: Int, height: Int): Long =
        clientProvider.get().sendImageMessage(conversationId, fileId, width, height)

    fun sendFileMessage(conversationId: Long, fileId: String, name: String, size: Long): Long =
        clientProvider.get().sendFileMessage(conversationId, fileId, name, size)

    fun sendAudioMessage(conversationId: Long, fileId: String, durationMs: Int): Long =
        clientProvider.get().sendAudioMessage(conversationId, fileId, durationMs)

    fun sendVideoMessage(conversationId: Long, fileId: String, width: Int, height: Int, durationMs: Int): Long =
        clientProvider.get().sendVideoMessage(conversationId, fileId, width, height, durationMs)

    fun sendMarkdownMessage(conversationId: Long, rawMarkdown: String): Long =
        clientProvider.get().sendMarkdownMessage(conversationId, rawMarkdown)

    fun sendCardMessage(conversationId: Long, cardJson: String, fallbackText: String): Long =
        clientProvider.get().sendCardMessage(conversationId, cardJson, fallbackText)

    fun editMessage(conversationId: Long, messageId: Long, text: String) {
        clientProvider.get().editMessage(conversationId, messageId, text)
    }

    fun recallMessage(conversationId: Long, messageId: Long) {
        clientProvider.getOrNull()?.recallMessage(conversationId, messageId)
    }

    fun deleteMessages(conversationId: Long, messageIds: List<Long>) {
        clientProvider.getOrNull()?.deleteMessages(conversationId, messageIds)
    }

    fun deleteHistory(conversationId: Long, upToMessageId: Long) {
        clientProvider.getOrNull()?.deleteHistory(conversationId, upToMessageId)
    }

    fun submitCardAction(conversationId: Long, messageId: Long, actionData: String, verb: String? = null): String =
        clientProvider.get().submitCardAction(conversationId, messageId, actionData, verb)
}
