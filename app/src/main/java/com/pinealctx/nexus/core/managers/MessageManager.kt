package com.pinealctx.nexus.core.managers

import com.pinealctx.nexus.client.MessageApi
import com.pinealctx.nexus.core.LocalMessageData
import com.pinealctx.nexus.core.MessageContent
import com.pinealctx.nexus.core.MessageData
import com.pinealctx.nexus.core.MessageSendState
import com.pinealctx.nexus.core.MessagePageData
import com.pinealctx.nexus.core.SecureStorage
import com.pinealctx.nexus.local.LocalDataStore
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageManager @Inject constructor(
    private val messageApi: MessageApi,
    private val localDataStore: LocalDataStore,
    private val secureStorage: SecureStorage
) {
    fun observeMessages(conversationId: String, limit: Int = 50): Flow<List<MessageData>> =
        localDataStore.observeMessages(conversationId, limit)

    fun observeLocalMessages(conversationId: String): Flow<List<LocalMessageData>> =
        localDataStore.observeLocalMessages(conversationId)

    suspend fun getMessages(conversationId: String, limit: Int = 50, beforeId: Long? = null): List<MessageData> {
        val convId = conversationId.toLongOrNull() ?: return emptyList()
        val cached = localDataStore.listMessages(convId, limit, beforeId)
        if (cached.isNotEmpty()) return cached

        return fetchMessagePage(conversationId, limit, beforeId).messages
    }

    suspend fun fetchMessagePage(
        conversationId: String,
        limit: Int = 50,
        beforeId: Long? = null
    ): MessagePageData {
        val convId = conversationId.toLongOrNull()
            ?: return MessagePageData(emptyList(), emptyList(), hasMore = false)
        val page = messageApi.getMessagePage(convId, limit, beforeId)
        localDataStore.upsertUsers(page.relatedUsers)
        localDataStore.upsertMessages(page.messages)
        return page
    }

    fun getLocalMessages(conversationId: String): List<LocalMessageData> {
        val convId = conversationId.toLongOrNull() ?: return emptyList()
        return localDataStore.listLocalMessages(convId)
    }

    fun currentUserId(): Int = secureStorage.getUserId()

    fun enqueueTextMessage(conversationId: Long, text: String, replyToMessageId: Long? = null): LocalMessageData {
        return enqueueMessage(conversationId, MessageContent.Text(text), replyToMessageId)
    }

    fun enqueueImageMessage(conversationId: Long, fileId: String, width: Int, height: Int): LocalMessageData {
        return enqueueMessage(conversationId, MessageContent.Image(fileId, width, height))
    }

    fun enqueueFileMessage(
        conversationId: Long,
        fileId: String,
        name: String,
        size: Long,
        mimeType: String = ""
    ): LocalMessageData {
        return enqueueMessage(conversationId, MessageContent.File(fileId, name, size, mimeType))
    }

    fun enqueueAudioMessage(
        conversationId: Long,
        fileId: String,
        durationMs: Int,
        sizeBytes: Long = 0
    ): LocalMessageData {
        return enqueueMessage(conversationId, MessageContent.Audio(fileId, durationMs, sizeBytes))
    }

    fun enqueueVideoMessage(
        conversationId: Long,
        fileId: String,
        width: Int,
        height: Int,
        durationMs: Int,
        thumbnailFileId: String = "",
        sizeBytes: Long = 0
    ): LocalMessageData {
        return enqueueMessage(
            conversationId,
            MessageContent.Video(fileId, durationMs, width, height, thumbnailFileId, sizeBytes)
        )
    }

    fun enqueueMarkdownMessage(conversationId: Long, rawMarkdown: String): LocalMessageData {
        return enqueueMessage(conversationId, MessageContent.Markdown(rawMarkdown))
    }

    fun enqueueCardMessage(conversationId: Long, cardJson: String, fallbackText: String): LocalMessageData {
        return enqueueMessage(conversationId, MessageContent.Card(cardJson, fallbackText))
    }

    private fun enqueueMessage(
        conversationId: Long,
        content: MessageContent,
        replyToMessageId: Long? = null
    ): LocalMessageData {
        val clientMessageId = messageApi.nextClientMessageId()
        val localMessage = LocalMessageData(
            clientMessageId = clientMessageId,
            conversationId = conversationId.toString(),
            serverMessageId = null,
            senderId = secureStorage.getUserId(),
            content = content,
            replyToMessageId = replyToMessageId,
            createdAt = System.currentTimeMillis(),
            sendState = MessageSendState.SENDING
        )
        localDataStore.upsertLocalMessage(localMessage)
        return localMessage
    }

    suspend fun sendQueuedTextMessage(message: LocalMessageData): Long {
        require(message.content is MessageContent.Text) {
            "Only text local messages can be sent with sendQueuedTextMessage"
        }
        return sendQueuedMessage(message)
    }

    suspend fun sendQueuedMessage(message: LocalMessageData): Long {
        return try {
            val serverMessageId = sendLocalMessageToServer(message)
            localDataStore.markLocalMessageSent(message.clientMessageId, serverMessageId)
            serverMessageId
        } catch (error: Exception) {
            localDataStore.markLocalMessageFailed(message.clientMessageId)
            throw error
        }
    }

    suspend fun retryLocalMessage(clientMessageId: Long): Long {
        val message = localDataStore.getLocalMessage(clientMessageId)
            ?: throw IllegalArgumentException("Local message not found")
        localDataStore.markLocalMessageSending(clientMessageId)
        return sendQueuedMessage(message)
    }

    suspend fun retryTextMessage(clientMessageId: Long): Long {
        val message = localDataStore.getLocalMessage(clientMessageId)
            ?: throw IllegalArgumentException("Local message not found")
        require(message.content is MessageContent.Text) {
            "Only text local messages can be retried"
        }
        return retryLocalMessage(clientMessageId)
    }

    fun markLocalMessageSending(clientMessageId: Long) {
        localDataStore.markLocalMessageSending(clientMessageId)
    }

    suspend fun sendMessage(conversationId: Long, text: String): Long {
        return sendQueuedMessage(enqueueTextMessage(conversationId, text))
    }

    suspend fun sendReplyMessage(conversationId: Long, text: String, replyToMessageId: Long): Long =
        messageApi.sendReplyMessage(conversationId, text, replyToMessageId)

    suspend fun sendImageMessage(conversationId: Long, fileId: String, width: Int, height: Int): Long {
        return sendQueuedMessage(enqueueImageMessage(conversationId, fileId, width, height))
    }

    suspend fun sendFileMessage(conversationId: Long, fileId: String, name: String, size: Long): Long {
        return sendQueuedMessage(enqueueFileMessage(conversationId, fileId, name, size))
    }

    suspend fun sendAudioMessage(conversationId: Long, fileId: String, durationMs: Int): Long {
        return sendQueuedMessage(enqueueAudioMessage(conversationId, fileId, durationMs))
    }

    suspend fun sendVideoMessage(conversationId: Long, fileId: String, width: Int, height: Int, durationMs: Int): Long {
        return sendQueuedMessage(enqueueVideoMessage(conversationId, fileId, width, height, durationMs))
    }

    suspend fun sendMarkdownMessage(conversationId: Long, rawMarkdown: String): Long {
        return sendQueuedMessage(enqueueMarkdownMessage(conversationId, rawMarkdown))
    }

    suspend fun sendCardMessage(conversationId: Long, cardJson: String, fallbackText: String): Long {
        return sendQueuedMessage(enqueueCardMessage(conversationId, cardJson, fallbackText))
    }

    suspend fun editMessage(conversationId: Long, messageId: Long, text: String) {
        messageApi.editMessage(conversationId, messageId, text)
        localDataStore.editMessage(conversationId, messageId, text)
    }

    suspend fun recallMessage(conversationId: Long, messageId: Long) {
        messageApi.recallMessage(conversationId, messageId)
        localDataStore.recallMessage(conversationId, messageId)
    }

    suspend fun deleteMessages(conversationId: Long, messageIds: List<Long>) {
        messageApi.deleteMessages(conversationId, messageIds)
        localDataStore.deleteMessages(conversationId, messageIds)
    }

    suspend fun deleteHistory(conversationId: Long, upToMessageId: Long) {
        messageApi.deleteHistory(conversationId, upToMessageId)
        localDataStore.deleteHistory(conversationId, upToMessageId)
    }

    suspend fun submitCardAction(
        conversationId: Long,
        messageId: Long,
        actionData: String,
        verb: String? = null
    ): String = messageApi.submitCardAction(conversationId, messageId, actionData, verb)

    private suspend fun sendLocalMessageToServer(message: LocalMessageData): Long {
        val conversationId = message.conversationId.toLong()
        return when (val content = message.content) {
            is MessageContent.Text -> messageApi.sendMessage(
                conversationId = conversationId,
                text = content.text,
                clientMessageId = message.clientMessageId,
                replyToMessageId = message.replyToMessageId
            )
            is MessageContent.Image -> messageApi.sendImageMessage(
                conversationId = conversationId,
                fileId = content.fileId,
                width = content.width,
                height = content.height,
                clientMessageId = message.clientMessageId
            )
            is MessageContent.File -> messageApi.sendFileMessage(
                conversationId = conversationId,
                fileId = content.fileId,
                name = content.name,
                size = content.size,
                clientMessageId = message.clientMessageId
            )
            is MessageContent.Audio -> messageApi.sendAudioMessage(
                conversationId = conversationId,
                fileId = content.fileId,
                durationMs = content.durationMs,
                sizeBytes = content.sizeBytes,
                clientMessageId = message.clientMessageId
            )
            is MessageContent.Video -> messageApi.sendVideoMessage(
                conversationId = conversationId,
                fileId = content.fileId,
                width = content.width,
                height = content.height,
                durationMs = content.durationMs,
                thumbnailFileId = content.thumbnailFileId,
                sizeBytes = content.sizeBytes,
                clientMessageId = message.clientMessageId
            )
            is MessageContent.Markdown -> messageApi.sendMarkdownMessage(
                conversationId = conversationId,
                rawMarkdown = content.text,
                clientMessageId = message.clientMessageId
            )
            is MessageContent.Card -> messageApi.sendCardMessage(
                conversationId = conversationId,
                cardJson = content.json,
                fallbackText = content.fallbackText,
                clientMessageId = message.clientMessageId
            )
            else -> throw IllegalArgumentException("Unsupported local message content: ${content.javaClass.simpleName}")
        }
    }
}
