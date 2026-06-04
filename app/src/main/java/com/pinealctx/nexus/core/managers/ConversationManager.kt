package com.pinealctx.nexus.core.managers

import com.pinealctx.nexus.client.ConversationApi
import com.pinealctx.nexus.core.ConversationData
import com.pinealctx.nexus.local.LocalDataStore
import com.shared.v1.ConversationActionType
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationManager @Inject constructor(
    private val conversationApi: ConversationApi,
    private val localDataStore: LocalDataStore
) {
    fun getConversations(limit: Int = 50, beforeTime: Long? = null): List<ConversationData> {
        val cached = localDataStore.listConversations(limit, beforeTime)
        if (cached.isNotEmpty()) return cached

        return runBlocking {
            conversationApi.listConversations(limit, beforeTime)
                .also { localDataStore.upsertConversations(it) }
        }
    }

    fun fetchConversations(limit: Int = 50, beforeTime: Long? = null): List<ConversationData> {
        return runBlocking {
            conversationApi.listConversations(limit, beforeTime)
                .also { localDataStore.upsertConversations(it) }
        }
    }

    fun getConversation(conversationId: Long): ConversationData? {
        return localDataStore.getConversation(conversationId)
            ?: runBlocking {
                conversationApi.getConversation(conversationId)
                    ?.also { localDataStore.upsertConversation(it) }
            }
    }

    fun markAsRead(conversationId: Long, upToMessageId: Long) {
        runBlocking { conversationApi.markAsRead(conversationId, upToMessageId) }
        localDataStore.markConversationRead(conversationId, upToMessageId)
    }

    fun muteConversation(conversationId: Long) {
        runBlocking { conversationApi.muteConversation(conversationId) }
        localDataStore.applyConversationAction(
            conversationId = conversationId,
            action = ConversationActionType.CONVERSATION_ACTION_TYPE_MUTE,
            clearMessages = false
        )
    }

    fun unmuteConversation(conversationId: Long) {
        runBlocking { conversationApi.unmuteConversation(conversationId) }
        localDataStore.applyConversationAction(
            conversationId = conversationId,
            action = ConversationActionType.CONVERSATION_ACTION_TYPE_UNMUTE,
            clearMessages = false
        )
    }

    fun deleteConversation(conversationId: Long, clearMessages: Boolean) {
        runBlocking { conversationApi.deleteConversation(conversationId, clearMessages) }
        localDataStore.applyConversationAction(
            conversationId = conversationId,
            action = ConversationActionType.CONVERSATION_ACTION_TYPE_DELETE,
            clearMessages = clearMessages
        )
    }
}
