package com.pinealctx.nexus.ui.screens.chat

import com.pinealctx.nexus.core.MessageContent

data class ChatMessageActionState(
    val canReply: Boolean,
    val canCopy: Boolean,
    val canEdit: Boolean,
    val canRecall: Boolean,
    val canDelete: Boolean,
    val isPending: Boolean,
    val copyText: String?
)

object ChatMessageActionPolicy {
    fun forMessage(
        message: ChatMessageItem,
        currentUserId: Int,
        pendingActionMessageId: Long?
    ): ChatMessageActionState {
        val remote = message as? ChatMessageItem.Remote
        val isPending = remote?.data?.messageId == pendingActionMessageId
        val copyText = message.copyableText()
        return ChatMessageActionState(
            canReply = remote != null && !remote.recalled && !isPending,
            canCopy = remote != null && !remote.recalled && copyText != null && !isPending,
            canEdit = remote != null &&
                !remote.recalled &&
                remote.senderId == currentUserId &&
                remote.content is MessageContent.Text &&
                !isPending,
            canRecall = remote != null &&
                !remote.recalled &&
                remote.senderId == currentUserId &&
                !isPending,
            canDelete = remote != null && !isPending,
            isPending = isPending,
            copyText = copyText
        )
    }

    private fun ChatMessageItem.copyableText(): String? {
        return when (val content = content) {
            is MessageContent.Text -> content.text
            is MessageContent.Markdown -> content.text
            else -> null
        }
    }
}
