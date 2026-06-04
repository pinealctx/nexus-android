package com.pinealctx.nexus.ui.screens.chat

import com.pinealctx.nexus.core.LocalMessageData
import com.pinealctx.nexus.core.MessageContent
import com.pinealctx.nexus.core.MessageData
import com.pinealctx.nexus.core.MessageSendState
import com.pinealctx.nexus.core.previewText

data class ChatReplyPreview(
    val messageId: Long,
    val senderId: Int,
    val senderNickname: String,
    val text: String
)

sealed interface ChatMessageItem {
    val stableId: String
    val senderId: Int
    val content: MessageContent
    val createdAt: Long
    val edited: Boolean
    val recalled: Boolean
    val replyToMessageId: Long?
    val replyPreview: ChatReplyPreview?
    val sendState: MessageSendState?

    data class Remote(
        val data: MessageData,
        override val replyPreview: ChatReplyPreview? = null
    ) : ChatMessageItem {
        override val stableId: String = "remote:${data.conversationId}:${data.messageId}"
        override val senderId: Int = data.senderId
        override val content: MessageContent = data.content
        override val createdAt: Long = data.createdAt
        override val edited: Boolean = data.edited
        override val recalled: Boolean = data.recalled
        override val replyToMessageId: Long? = data.replyToMessageId
        override val sendState: MessageSendState? = null
    }

    data class Local(
        val data: LocalMessageData,
        override val replyPreview: ChatReplyPreview? = null
    ) : ChatMessageItem {
        override val stableId: String = "local:${data.clientMessageId}"
        override val senderId: Int = data.senderId
        override val content: MessageContent = data.content
        override val createdAt: Long = data.createdAt
        override val edited: Boolean = false
        override val recalled: Boolean = false
        override val replyToMessageId: Long? = data.replyToMessageId
        override val sendState: MessageSendState = data.sendState
    }
}

object ChatMessageMerger {
    fun merge(
        remoteMessages: List<MessageData>,
        localMessages: List<LocalMessageData>
    ): List<ChatMessageItem> {
        val remoteIds = remoteMessages.mapTo(mutableSetOf()) { it.messageId }
        val replyPreviewByMessageId = remoteMessages.associate { message ->
            message.messageId to ChatReplyPreview(
                messageId = message.messageId,
                senderId = message.senderId,
                senderNickname = "",
                text = message.content.previewText()
            )
        }
        val visibleLocal = localMessages.filter { local ->
            local.serverMessageId == null || local.serverMessageId !in remoteIds
        }
        val remoteItems = remoteMessages.map { message ->
            val persistedReplyPreview = message.replyContext?.let { replyContext ->
                ChatReplyPreview(
                    messageId = replyContext.messageId,
                    senderId = replyContext.senderId,
                    senderNickname = replyContext.senderNickname,
                    text = replyContext.contentPreview
                )
            }
            ChatMessageItem.Remote(
                data = message,
                replyPreview = persistedReplyPreview
                    ?: message.replyToMessageId?.let { replyPreviewByMessageId[it] }
            )
        }
        val localItems = visibleLocal.map { local ->
            ChatMessageItem.Local(
                data = local,
                replyPreview = local.replyToMessageId?.let { replyPreviewByMessageId[it] }
            )
        }
        return (remoteItems + localItems)
            .sortedWith(
                compareByDescending<ChatMessageItem> { it.createdAt }
                    .thenByDescending { item ->
                        when (item) {
                            is ChatMessageItem.Remote -> item.data.messageId
                            is ChatMessageItem.Local -> item.data.clientMessageId
                        }
                    }
            )
    }
}

object ChatMessageIndex {
    fun remoteIndexOf(messages: List<ChatMessageItem>, messageId: Long): Int {
        return messages.indexOfFirst { message ->
            message is ChatMessageItem.Remote && message.data.messageId == messageId
        }
    }
}
