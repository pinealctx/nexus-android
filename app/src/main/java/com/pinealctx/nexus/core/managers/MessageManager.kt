package com.pinealctx.nexus.core.managers

import com.pinealctx.nexus.client.MessageApi
import com.pinealctx.nexus.core.MessageData
import com.pinealctx.nexus.local.LocalDataStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking

@Singleton
class MessageManager @Inject constructor(
    private val messageApi: MessageApi,
    private val localDataStore: LocalDataStore
) {
    fun getMessages(conversationId: String, limit: Int = 50, beforeId: Long? = null): List<MessageData> {
        val convId = conversationId.toLongOrNull() ?: return emptyList()
        val cached = localDataStore.listMessages(convId, limit, beforeId)
        if (cached.isNotEmpty()) return cached

        return runBlocking {
            messageApi.getMessages(convId, limit, beforeId)
                .also { localDataStore.upsertMessages(it) }
        }
    }

    fun sendMessage(conversationId: Long, text: String): Long =
        runBlocking { messageApi.sendMessage(conversationId, text) }

    fun sendReplyMessage(conversationId: Long, text: String, replyToMessageId: Long): Long =
        runBlocking { messageApi.sendReplyMessage(conversationId, text, replyToMessageId) }

    fun sendImageMessage(conversationId: Long, fileId: String, width: Int, height: Int): Long =
        runBlocking { messageApi.sendImageMessage(conversationId, fileId, width, height) }

    fun sendFileMessage(conversationId: Long, fileId: String, name: String, size: Long): Long =
        runBlocking { messageApi.sendFileMessage(conversationId, fileId, name, size) }

    fun sendAudioMessage(conversationId: Long, fileId: String, durationMs: Int): Long =
        runBlocking { messageApi.sendAudioMessage(conversationId, fileId, durationMs) }

    fun sendVideoMessage(conversationId: Long, fileId: String, width: Int, height: Int, durationMs: Int): Long =
        runBlocking { messageApi.sendVideoMessage(conversationId, fileId, width, height, durationMs) }

    fun sendMarkdownMessage(conversationId: Long, rawMarkdown: String): Long =
        runBlocking { messageApi.sendMarkdownMessage(conversationId, rawMarkdown) }

    fun sendCardMessage(conversationId: Long, cardJson: String, fallbackText: String): Long =
        runBlocking { messageApi.sendCardMessage(conversationId, cardJson, fallbackText) }

    fun editMessage(conversationId: Long, messageId: Long, text: String) {
        runBlocking { messageApi.editMessage(conversationId, messageId, text) }
    }

    fun recallMessage(conversationId: Long, messageId: Long) {
        runBlocking { messageApi.recallMessage(conversationId, messageId) }
    }

    fun deleteMessages(conversationId: Long, messageIds: List<Long>) {
        runBlocking { messageApi.deleteMessages(conversationId, messageIds) }
        localDataStore.deleteMessages(conversationId, messageIds)
    }

    fun deleteHistory(conversationId: Long, upToMessageId: Long) {
        runBlocking { messageApi.deleteHistory(conversationId, upToMessageId) }
        localDataStore.deleteHistory(conversationId, upToMessageId)
    }

    fun submitCardAction(conversationId: Long, messageId: Long, actionData: String, verb: String? = null): String =
        runBlocking { messageApi.submitCardAction(conversationId, messageId, actionData, verb) }
}
