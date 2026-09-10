package com.pinealctx.nexus.client

import com.pinealctx.nexus.core.ConversationData
import com.pinealctx.nexus.core.ContactData
import com.pinealctx.nexus.core.MessageContent
import com.pinealctx.nexus.core.MessageData
import com.pinealctx.nexus.core.MessageReplyContextData
import com.pinealctx.nexus.core.MessageStreamPhase
import com.shared.v1.ConversationInfo
import com.shared.v1.ConversationType
import com.shared.v1.GroupInfo
import com.shared.v1.MessageBody
import com.shared.v1.MessageEnvelope
import com.shared.v1.MessageType
import com.shared.v1.UserInfo

internal fun UserInfo.toRelatedContactData(): ContactData = ContactData(
    userId = userId,
    username = username,
    nickname = nickname,
    avatarUrl = avatarUrl,
    alias = null
)

internal fun ConversationInfo.toConversationData(
    user: UserInfo? = null,
    group: GroupInfo? = null,
    lastMessage: MessageEnvelope? = null
): ConversationData {
    val isGroup = type == ConversationType.CONVERSATION_TYPE_GROUP
    return ConversationData(
        conversationId = conversationId.toString(),
        conversationType = type,
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
        recalled = content is MessageContent.Recalled || (hasBody() && body.type == MessageType.MESSAGE_TYPE_RECALLED),
        reactions = if (hasReactions()) reactions else null
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
    if (type == MessageType.MESSAGE_TYPE_GROUP && contentCase != MessageBody.ContentCase.GROUP) {
        return MessageContent.GroupEvent(0, com.pinealctx.nexus.core.GroupEventType.UNKNOWN)
    }
    if (type == MessageType.MESSAGE_TYPE_RECALLED) return MessageContent.Recalled

    return when (contentCase) {
        MessageBody.ContentCase.TEXT -> MessageContent.Text(text.text, text.entitiesList.map { it.toTextEntityData() })
        MessageBody.ContentCase.IMAGE -> MessageContent.Image(image.fileId, image.width, image.height)
        MessageBody.ContentCase.AUDIO -> MessageContent.Audio(
            fileId = audio.fileId,
            durationMs = audio.durationMs,
            sizeBytes = audio.sizeBytes,
            transcript = if (audio.hasTranscript()) audio.transcript else null
        )
        MessageBody.ContentCase.VIDEO -> MessageContent.Video(
            fileId = video.fileId,
            durationMs = video.durationMs,
            width = video.width,
            height = video.height,
            thumbnailFileId = video.thumbnailFileId,
            sizeBytes = video.sizeBytes
        )
        MessageBody.ContentCase.FILE -> MessageContent.File(file.fileId, file.filename, file.sizeBytes, file.mimeType)
        MessageBody.ContentCase.MARKDOWN -> MessageContent.Markdown(markdown.rawMarkdown)
        MessageBody.ContentCase.CARD -> MessageContent.Card(card.cardJson, card.fallbackText)
        MessageBody.ContentCase.STREAM -> MessageContent.Stream(
            phase = MessageStreamPhase.fromCode(stream.phaseValue),
            sequence = stream.seq,
            delta = stream.delta,
            contentType = stream.contentType,
            accumulatedText = stream.accumulatedText,
            errorMessage = stream.errorMessage.takeIf { it.isNotBlank() }
        )
        MessageBody.ContentCase.RECALLED -> MessageContent.Recalled
        MessageBody.ContentCase.GROUP -> group.toMessageContent()
        MessageBody.ContentCase.CUSTOM_PAYLOAD,
        MessageBody.ContentCase.CONTENT_NOT_SET -> MessageContent.Unknown
    }
}

private fun com.shared.v1.GroupContent.toMessageContent(): MessageContent.GroupEvent {
    return when (eventCase) {
        com.shared.v1.GroupContent.EventCase.MEMBER_JOINED -> MessageContent.GroupEvent(
            groupId = groupId,
            type = com.pinealctx.nexus.core.GroupEventType.MEMBER_JOINED,
            memberIds = memberJoined.membersList.map { it.userId },
            inviterId = memberJoined.takeIf { it.hasInviter() }?.inviter?.userId
        )
        com.shared.v1.GroupContent.EventCase.MEMBER_LEFT -> MessageContent.GroupEvent(
            groupId = groupId,
            type = com.pinealctx.nexus.core.GroupEventType.MEMBER_LEFT,
            memberIds = memberLeft.takeIf { it.hasMember() }?.let { listOf(it.member.userId) }.orEmpty()
        )
        com.shared.v1.GroupContent.EventCase.MEMBER_REMOVED -> MessageContent.GroupEvent(
            groupId = groupId,
            type = com.pinealctx.nexus.core.GroupEventType.MEMBER_REMOVED,
            memberIds = memberRemoved.takeIf { it.hasMember() }?.let { listOf(it.member.userId) }.orEmpty(),
            operatorId = memberRemoved.takeIf { it.hasOperator() }?.operator?.userId
        )
        com.shared.v1.GroupContent.EventCase.GROUP_INFO_CHANGED -> MessageContent.GroupEvent(
            groupId = groupId,
            type = com.pinealctx.nexus.core.GroupEventType.GROUP_INFO_CHANGED,
            operatorId = groupInfoChanged.takeIf { it.hasOperator() }?.operator?.userId,
            changedField = groupInfoChanged.field.takeIf { it.isNotBlank() }
        )
        com.shared.v1.GroupContent.EventCase.EVENT_NOT_SET,
        null -> MessageContent.GroupEvent(groupId, com.pinealctx.nexus.core.GroupEventType.UNKNOWN)
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
