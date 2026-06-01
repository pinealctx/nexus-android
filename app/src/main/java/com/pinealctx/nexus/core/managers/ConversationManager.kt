package com.pinealctx.nexus.core.managers

import com.pinealctx.nexus.core.ConversationData
import com.pinealctx.nexus.core.NexusClientProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationManager @Inject constructor(
    private val clientProvider: NexusClientProvider
) {
    fun getConversations(limit: Int = 50, beforeTime: Long? = null): List<ConversationData> {
        val conversations = clientProvider.getOrNull()?.getConversations(limit, beforeTime) ?: return emptyList()
        return conversations.map { it.toData() }
    }

    fun fetchConversations(limit: Int = 50, beforeTime: Long? = null): List<ConversationData> {
        val conversations = clientProvider.getOrNull()?.fetchConversations(limit, beforeTime) ?: return emptyList()
        return conversations.map { it.toData() }
    }

    fun getConversation(conversationId: Long): ConversationData? {
        return clientProvider.getOrNull()?.getConversation(conversationId)?.toData()
    }

    fun markAsRead(conversationId: Long, upToMessageId: Long) {
        clientProvider.getOrNull()?.markAsRead(conversationId, upToMessageId)
    }

    fun muteConversation(conversationId: Long) { clientProvider.getOrNull()?.muteConversation(conversationId) }

    fun unmuteConversation(conversationId: Long) { clientProvider.getOrNull()?.unmuteConversation(conversationId) }

    fun deleteConversation(conversationId: Long, clearMessages: Boolean) {
        clientProvider.getOrNull()?.deleteConversation(conversationId, clearMessages)
    }
}

private fun uniffi.nexus_ffi.ConversationInfo.toData() = ConversationData(
    conversationId, conversationType, peerId, lastMessageId, lastMessageTime, lastMessagePreview, isMuted, readUpToMessageId
)
