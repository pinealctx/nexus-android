package com.pinealctx.nexus.data.repository

import com.pinealctx.nexus.core.ConversationData
import com.pinealctx.nexus.core.managers.ConversationManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationRepository @Inject constructor(
    private val conversationManager: ConversationManager
) {
    suspend fun fetchPage(limit: Int = 50, beforeTime: Long? = null) = conversationManager.fetchConversationPage(limit, beforeTime)

    fun observeConversations(limit: Int = 50, beforeTime: Long? = null): Flow<List<ConversationData>> =
        conversationManager.observeConversations(limit, beforeTime)

    suspend fun getConversations(limit: Int = 50, beforeTime: Long? = null): List<ConversationData> {
        return conversationManager.getConversations(limit, beforeTime)
    }

    suspend fun fetchFromRemote(limit: Int = 50, beforeTime: Long? = null): List<ConversationData> {
        return conversationManager.fetchConversations(limit, beforeTime)
    }

    suspend fun markAsRead(conversationId: Long, upToMessageId: Long) {
        conversationManager.markAsRead(conversationId, upToMessageId)
    }

    suspend fun mute(conversationId: Long) {
        conversationManager.muteConversation(conversationId)
    }

    suspend fun unmute(conversationId: Long) {
        conversationManager.unmuteConversation(conversationId)
    }

    suspend fun delete(conversationId: Long, clearMessages: Boolean) {
        conversationManager.deleteConversation(conversationId, clearMessages)
    }
}
