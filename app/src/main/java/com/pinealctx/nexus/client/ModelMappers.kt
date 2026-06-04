package com.pinealctx.nexus.client

import com.pinealctx.nexus.core.ConversationData
import com.pinealctx.nexus.core.MessageContent
import com.pinealctx.nexus.core.MessageData
import com.pinealctx.nexus.core.MessageReplyContextData
import com.shared.v1.ConversationInfo
import com.shared.v1.ConversationType
import com.shared.v1.GroupInfo
import com.shared.v1.MessageBody
import com.shared.v1.MessageEnvelope
import com.shared.v1.MessageType
import com.shared.v1.UserInfo

internal fun ConversationInfo.toConversationData(
    user: UserInfo? = null,
    group: GroupInfo? = null,
    lastMessage: MessageEnvelope? = null
): ConversationData {
    val isGroup = type == ConversationType.CONVERSATION_TYPE_GROUP
    return ConversationData(
        conversationId = conversationId.toString(),
        conversationType = type.number,
        peerId = peerId,
        displayName = if (isGroup) group?.name else user?.nickname?.takeIf { it.isNotBlank() } ?: user?.username,
        avatarUrl = if (isGroup) group?.avatarUrl else user?.avatarUrl,
        lastMessageId = lastMessageId,
        lastMessageTime = lastMessageTime,
        lastMessageContent = lastMessage?.previewText(),
        isMuted = isMuted,
        lastReadMessageId = lastReadMessageId
    )
}

internal fun MessageEnvelope.toMessageData(): MessageData {
    val content = if (hasBody()) body.toMessageContent() else MessageContent.Unknown
    return MessageData(
        conversationId = conversationId.toString(),
        messageId = messageId,
        senderId = senderId,
        content = content,
        replyToMessageId = if (hasReplyTo()) replyTo.messageId else null,
        replyContext = if (hasReplyTo()) replyTo.toMessageReplyContextData() else null,
        createdAt = createdAt,
        edited = edited,
        recalled = content is MessageContent.Recalled || (hasBody() && body.type == MessageType.MESSAGE_TYPE_RECALLED)
    )
}

private fun com.shared.v1.ReplyContext.toMessageReplyContextData(): MessageReplyContextData {
    return MessageReplyContextData(
        messageId = messageId,
        senderId = senderId,
        senderNickname = senderNickname,
        contentPreview = contentPreview
    )
}

internal fun MessageBody.toMessageContent(): MessageContent {
    return when (contentCase) {
        MessageBody.ContentCase.TEXT -> MessageContent.Text(text.text)
        MessageBody.ContentCase.IMAGE -> MessageContent.Image(image.fileId, image.width, image.height)
        MessageBody.ContentCase.AUDIO -> MessageContent.Audio(audio.fileId, audio.durationMs / 1000)
        MessageBody.ContentCase.VIDEO -> MessageContent.Video(video.fileId, video.durationMs / 1000, video.width, video.height)
        MessageBody.ContentCase.FILE -> MessageContent.File(file.fileId, file.filename, file.sizeBytes, file.mimeType)
        MessageBody.ContentCase.MARKDOWN -> MessageContent.Markdown(markdown.rawMarkdown)
        MessageBody.ContentCase.CARD -> MessageContent.Card(card.cardJson, card.fallbackText)
        MessageBody.ContentCase.STREAM -> stream.accumulatedText.takeIf { it.isNotBlank() }
            ?.let { MessageContent.Text(it) }
            ?: MessageContent.Unknown
        MessageBody.ContentCase.RECALLED -> MessageContent.Recalled
        MessageBody.ContentCase.GROUP,
        MessageBody.ContentCase.CUSTOM_PAYLOAD,
        MessageBody.ContentCase.CONTENT_NOT_SET -> MessageContent.Unknown
    }
}

internal fun MessageEnvelope.previewText(): String? {
    if (!hasBody()) return null
    return body.previewText()
}

internal fun MessageBody.previewText(): String {
    return when (contentCase) {
        MessageBody.ContentCase.TEXT -> text.text
        MessageBody.ContentCase.IMAGE -> "[Image]"
        MessageBody.ContentCase.AUDIO -> "[Audio]"
        MessageBody.ContentCase.VIDEO -> "[Video]"
        MessageBody.ContentCase.FILE -> "[File] ${file.filename}"
        MessageBody.ContentCase.MARKDOWN -> markdown.rawMarkdown
        MessageBody.ContentCase.CARD -> card.fallbackText.ifBlank { "[Card]" }
        MessageBody.ContentCase.STREAM -> stream.accumulatedText.ifBlank { "[Stream]" }
        MessageBody.ContentCase.GROUP -> "[Group update]"
        MessageBody.ContentCase.RECALLED -> "[Message recalled]"
        MessageBody.ContentCase.CUSTOM_PAYLOAD -> "[Message]"
        MessageBody.ContentCase.CONTENT_NOT_SET -> ""
    }
}
