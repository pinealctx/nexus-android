package com.pinealctx.nexus.core.managers

import com.pinealctx.nexus.client.ConversationApi
import com.pinealctx.nexus.core.ConversationData
import com.pinealctx.nexus.local.LocalDataStore
import com.shared.v1.ConversationActionType
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationManager @Inject constructor(
    private val conversationApi: ConversationApi,
    private val localDataStore: LocalDataStore
) {
    fun observeConversations(
        limit: Int = 50,
        beforeTime: Long? = null
    ): Flow<List<ConversationData>> = localDataStore.observeConversations(limit, beforeTime)

    suspend fun getConversations(limit: Int = 50, beforeTime: Long? = null): List<ConversationData> {
        val cached = localDataStore.listConversations(limit, beforeTime)
        if (cached.isNotEmpty()) return cached

        return fetchConversations(limit, beforeTime)
    }

    suspend fun fetchConversationPage(limit: Int = 50, beforeTime: Long? = null): com.pinealctx.nexus.core.ConversationPageData {
        val versions = localDataStore.conversationVersions()
        return conversationApi.listConversationPage(limit, beforeTime)
            .also { localDataStore.upsertConversations(it.conversations, versions) }
    }

    suspend fun fetchConversations(limit: Int = 50, beforeTime: Long? = null): List<ConversationData> =
        fetchConversationPage(limit, beforeTime).conversations

    suspend fun fetchAllConversations(pageSize: Int = 100): List<ConversationData> {
        val conversations = mutableListOf<ConversationData>()
        var beforeTime: Long? = null

        while (true) {
            val page = fetchConversationPage(pageSize, beforeTime)
            if (page.conversations.isEmpty()) break

            conversations += page.conversations
            if (!page.hasMore) break

            val nextBeforeTime = page.conversations.last().lastMessageTime
            if (nextBeforeTime <= 0L || nextBeforeTime == beforeTime) break
            beforeTime = nextBeforeTime
        }

        return conversations.distinctBy { it.conversationId }
    }

    suspend fun getConversation(conversationId: Long): ConversationData? {
        return localDataStore.getConversation(conversationId)
            ?: conversationApi.getConversation(conversationId)
                ?.also { localDataStore.upsertConversation(it) }
    }

    suspend fun markAsRead(conversationId: Long, upToMessageId: Long) {
        conversationApi.markAsRead(conversationId, upToMessageId)
        localDataStore.markConversationRead(conversationId, upToMessageId)
    }

    fun getCachedConversation(conversationId: Long): ConversationData? = localDataStore.getConversation(conversationId)

    suspend fun muteConversation(conversationId: Long) {
        conversationApi.muteConversation(conversationId)
        localDataStore.applyConversationAction(
            conversationId = conversationId,
            action = ConversationActionType.CONVERSATION_ACTION_TYPE_MUTE,
            clearMessages = false
        )
    }

    suspend fun unmuteConversation(conversationId: Long) {
        conversationApi.unmuteConversation(conversationId)
        localDataStore.applyConversationAction(
            conversationId = conversationId,
            action = ConversationActionType.CONVERSATION_ACTION_TYPE_UNMUTE,
            clearMessages = false
        )
    }

    suspend fun deleteConversation(conversationId: Long, clearMessages: Boolean) {
        conversationApi.deleteConversation(conversationId, clearMessages)
        localDataStore.applyConversationAction(
            conversationId = conversationId,
            action = ConversationActionType.CONVERSATION_ACTION_TYPE_DELETE,
            clearMessages = clearMessages
        )
    }
}
