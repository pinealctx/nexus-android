package com.pinealctx.nexus.client

import com.api.v1.GetConversationRequest
import com.api.v1.ListConversationsRequest
import com.api.v1.MarkAsReadRequest
import com.api.v1.UpdateConversationActionRequest
import com.pinealctx.nexus.core.ConversationData
import com.shared.v1.ConversationActionType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationApi @Inject constructor(
    private val apiClientFactory: ApiClientFactory,
    private val headers: RpcHeaders
) {
    suspend fun listConversations(limit: Int = 50, beforeTime: Long? = null): List<ConversationData> {
        val request = ListConversationsRequest.newBuilder()
            .setLimit(limit)
            .apply {
                beforeTime?.let { setBeforeTime(it) }
            }
            .build()

        val response = apiClientFactory.createClients()
            .conversations
            .listConversations(request, headers.current())
            .requireMessage()

        val users = response.relatedUsersList.associateBy { it.userId }
        val groups = response.relatedGroupsList.associateBy { it.groupId }
        val messages = response.messagesList.associateBy { it.conversationId }

        return response.conversationsList.map { conversation ->
            conversation.toConversationData(
                user = users[conversation.peerId],
                group = groups[conversation.peerId],
                lastMessage = messages[conversation.conversationId]
            )
        }
    }

    suspend fun getConversation(conversationId: Long): ConversationData? {
        val response = apiClientFactory.createClients()
            .conversations
            .getConversation(
                request = GetConversationRequest.newBuilder()
                    .setConversationId(conversationId)
                    .build(),
                headers = headers.current()
            )
            .requireMessage()

        return if (response.hasConversation()) {
            response.conversation.toConversationData()
        } else {
            null
        }
    }

    suspend fun markAsRead(conversationId: Long, upToMessageId: Long) {
        apiClientFactory.createClients()
            .conversations
            .markAsRead(
                request = MarkAsReadRequest.newBuilder()
                    .setConversationId(conversationId)
                    .setUpToMessageId(upToMessageId)
                    .build(),
                headers = headers.current()
            )
            .requireMessage()
    }

    suspend fun muteConversation(conversationId: Long) {
        updateConversationAction(conversationId, ConversationActionType.CONVERSATION_ACTION_TYPE_MUTE)
    }

    suspend fun unmuteConversation(conversationId: Long) {
        updateConversationAction(conversationId, ConversationActionType.CONVERSATION_ACTION_TYPE_UNMUTE)
    }

    suspend fun deleteConversation(conversationId: Long, clearMessages: Boolean) {
        updateConversationAction(
            conversationId = conversationId,
            action = ConversationActionType.CONVERSATION_ACTION_TYPE_DELETE,
            clearMessages = clearMessages
        )
    }

    private suspend fun updateConversationAction(
        conversationId: Long,
        action: ConversationActionType,
        clearMessages: Boolean = false
    ) {
        apiClientFactory.createClients()
            .conversations
            .updateConversationAction(
                request = UpdateConversationActionRequest.newBuilder()
                    .setConversationId(conversationId)
                    .setAction(action)
                    .setClearMessages(clearMessages)
                    .build(),
                headers = headers.current()
            )
            .requireMessage()
    }
}
