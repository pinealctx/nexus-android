package com.pinealctx.nexus.client

import com.api.v1.DeleteHistoryRequest
import com.api.v1.DeleteMessagesRequest
import com.api.v1.EditMessageRequest
import com.api.v1.GetMessageHistoryRequest
import com.api.v1.RecallMessageRequest
import com.api.v1.SendMessageRequest
import com.api.v1.SubmitCardActionRequest
import com.pinealctx.nexus.core.MessageData
import com.shared.v1.AudioContent
import com.shared.v1.CardContent
import com.shared.v1.FileContent
import com.shared.v1.ImageContent
import com.shared.v1.MarkdownContent
import com.shared.v1.MessageBody
import com.shared.v1.MessageType
import com.shared.v1.TextContent
import com.shared.v1.VideoContent
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageApi @Inject constructor(
    private val apiClientFactory: ApiClientFactory,
    private val headers: RpcHeaders
) {
    private val clientMessageIds = AtomicLong(System.currentTimeMillis() shl 20)

    fun nextClientMessageId(): Long = clientMessageIds.incrementAndGet()

    suspend fun getMessages(conversationId: Long, limit: Int = 50, beforeId: Long? = null): List<MessageData> {
        val request = GetMessageHistoryRequest.newBuilder()
            .setConversationId(conversationId)
            .setLimit(limit)
            .apply {
                beforeId?.let { setBeforeMessageId(it) }
            }
            .build()

        return apiClientFactory.createClients()
            .messages
            .getMessageHistory(request, headers.current())
            .requireMessage()
            .messagesList
            .map { it.toMessageData() }
    }

    suspend fun sendMessage(
        conversationId: Long,
        text: String,
        clientMessageId: Long = nextClientMessageId(),
        replyToMessageId: Long? = null
    ): Long =
        send(
            conversationId = conversationId,
            body = textBody(text),
            clientMessageId = clientMessageId,
            replyToMessageId = replyToMessageId
        )

    suspend fun sendReplyMessage(conversationId: Long, text: String, replyToMessageId: Long): Long =
        sendMessage(
            conversationId = conversationId,
            text = text,
            clientMessageId = nextClientMessageId(),
            replyToMessageId = replyToMessageId
        )

    suspend fun sendImageMessage(
        conversationId: Long,
        fileId: String,
        width: Int,
        height: Int,
        clientMessageId: Long = nextClientMessageId()
    ): Long =
        send(conversationId, imageBody(fileId, width, height), clientMessageId = clientMessageId)

    suspend fun sendFileMessage(
        conversationId: Long,
        fileId: String,
        name: String,
        size: Long,
        clientMessageId: Long = nextClientMessageId()
    ): Long =
        send(conversationId, fileBody(fileId, name, size), clientMessageId = clientMessageId)

    suspend fun sendAudioMessage(
        conversationId: Long,
        fileId: String,
        durationMs: Int,
        clientMessageId: Long = nextClientMessageId()
    ): Long =
        send(conversationId, audioBody(fileId, durationMs), clientMessageId = clientMessageId)

    suspend fun sendVideoMessage(
        conversationId: Long,
        fileId: String,
        width: Int,
        height: Int,
        durationMs: Int,
        clientMessageId: Long = nextClientMessageId()
    ): Long =
        send(conversationId, videoBody(fileId, width, height, durationMs), clientMessageId = clientMessageId)

    suspend fun sendMarkdownMessage(
        conversationId: Long,
        rawMarkdown: String,
        clientMessageId: Long = nextClientMessageId()
    ): Long =
        send(conversationId, markdownBody(rawMarkdown), clientMessageId = clientMessageId)

    suspend fun sendCardMessage(
        conversationId: Long,
        cardJson: String,
        fallbackText: String,
        clientMessageId: Long = nextClientMessageId()
    ): Long =
        send(conversationId, cardBody(cardJson, fallbackText), clientMessageId = clientMessageId)

    suspend fun editMessage(conversationId: Long, messageId: Long, text: String) {
        apiClientFactory.createClients()
            .messages
            .editMessage(
                request = EditMessageRequest.newBuilder()
                    .setConversationId(conversationId)
                    .setMessageId(messageId)
                    .setNewBody(textBody(text))
                    .build(),
                headers = headers.current()
            )
            .requireMessage()
    }

    suspend fun recallMessage(conversationId: Long, messageId: Long) {
        apiClientFactory.createClients()
            .messages
            .recallMessage(
                request = RecallMessageRequest.newBuilder()
                    .setConversationId(conversationId)
                    .setMessageId(messageId)
                    .build(),
                headers = headers.current()
            )
            .requireMessage()
    }

    suspend fun deleteMessages(conversationId: Long, messageIds: List<Long>) {
        if (messageIds.isEmpty()) return
        apiClientFactory.createClients()
            .messages
            .deleteMessages(
                request = DeleteMessagesRequest.newBuilder()
                    .setConversationId(conversationId)
                    .addAllMessageIds(messageIds)
                    .build(),
                headers = headers.current()
            )
            .requireMessage()
    }

    suspend fun deleteHistory(conversationId: Long, upToMessageId: Long) {
        apiClientFactory.createClients()
            .messages
            .deleteHistory(
                request = DeleteHistoryRequest.newBuilder()
                    .setConversationId(conversationId)
                    .setUpToMessageId(upToMessageId)
                    .build(),
                headers = headers.current()
            )
            .requireMessage()
    }

    suspend fun submitCardAction(
        conversationId: Long,
        messageId: Long,
        actionData: String,
        verb: String? = null
    ): String {
        val response = apiClientFactory.createClients()
            .messages
            .submitCardAction(
                request = SubmitCardActionRequest.newBuilder()
                    .setConversationId(conversationId)
                    .setMessageId(messageId)
                    .setActionData(actionData)
                    .apply {
                        verb?.let { setVerb(it) }
                    }
                    .build(),
                headers = headers.current()
            )
            .requireMessage()

        return response.actionId
    }

    private suspend fun send(
        conversationId: Long,
        body: MessageBody,
        clientMessageId: Long,
        replyToMessageId: Long? = null
    ): Long {
        val request = SendMessageRequest.newBuilder()
            .setClientMessageId(clientMessageId)
            .setConversationId(conversationId)
            .setBody(body)
            .apply {
                replyToMessageId?.let { setReplyToMessageId(it) }
            }
            .build()

        return apiClientFactory.createClients()
            .messages
            .sendMessage(request, headers.current())
            .requireMessage()
            .messageId
    }
}

private fun textBody(text: String): MessageBody =
    MessageBody.newBuilder()
        .setType(MessageType.MESSAGE_TYPE_TEXT)
        .setText(TextContent.newBuilder().setText(text))
        .build()

private fun imageBody(fileId: String, width: Int, height: Int): MessageBody =
    MessageBody.newBuilder()
        .setType(MessageType.MESSAGE_TYPE_IMAGE)
        .setImage(
            ImageContent.newBuilder()
                .setFileId(fileId)
                .setWidth(width)
                .setHeight(height)
        )
        .build()

private fun fileBody(fileId: String, name: String, size: Long): MessageBody =
    MessageBody.newBuilder()
        .setType(MessageType.MESSAGE_TYPE_FILE)
        .setFile(
            FileContent.newBuilder()
                .setFileId(fileId)
                .setFilename(name)
                .setSizeBytes(size)
        )
        .build()

private fun audioBody(fileId: String, durationMs: Int): MessageBody =
    MessageBody.newBuilder()
        .setType(MessageType.MESSAGE_TYPE_AUDIO)
        .setAudio(
            AudioContent.newBuilder()
                .setFileId(fileId)
                .setDurationMs(durationMs)
        )
        .build()

private fun videoBody(fileId: String, width: Int, height: Int, durationMs: Int): MessageBody =
    MessageBody.newBuilder()
        .setType(MessageType.MESSAGE_TYPE_VIDEO)
        .setVideo(
            VideoContent.newBuilder()
                .setFileId(fileId)
                .setWidth(width)
                .setHeight(height)
                .setDurationMs(durationMs)
        )
        .build()

private fun markdownBody(rawMarkdown: String): MessageBody =
    MessageBody.newBuilder()
        .setType(MessageType.MESSAGE_TYPE_MARKDOWN)
        .setMarkdown(MarkdownContent.newBuilder().setRawMarkdown(rawMarkdown))
        .build()

private fun cardBody(cardJson: String, fallbackText: String): MessageBody =
    MessageBody.newBuilder()
        .setType(MessageType.MESSAGE_TYPE_CARD)
        .setCard(
            CardContent.newBuilder()
                .setCardJson(cardJson)
                .setFallbackText(fallbackText)
        )
        .build()
