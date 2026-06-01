package com.pinealctx.nexus.data.repository

import com.pinealctx.nexus.core.AppEvent
import com.pinealctx.nexus.core.AppEventBus
import com.pinealctx.nexus.core.ConversationData
import com.pinealctx.nexus.core.managers.ConversationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationRepository @Inject constructor(
    private val conversationManager: ConversationManager,
    private val appEventBus: AppEventBus
) {
    fun observeConversations(limit: Int = 50, beforeTime: Long? = null): Flow<List<ConversationData>> {
        return appEventBus.conversationsUpdated()
            .onStart { emit(AppEvent.ConversationsUpdated) }
            .map { conversationManager.getConversations(limit, beforeTime) }
            .flowOn(Dispatchers.IO)
    }

    fun getConversations(limit: Int = 50, beforeTime: Long? = null): List<ConversationData> {
        return conversationManager.getConversations(limit, beforeTime)
    }

    suspend fun fetchFromRemote(limit: Int = 50, beforeTime: Long? = null) {
        conversationManager.fetchConversations(limit, beforeTime)
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
