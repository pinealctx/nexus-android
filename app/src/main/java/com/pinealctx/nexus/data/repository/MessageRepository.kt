package com.pinealctx.nexus.data.repository

import com.pinealctx.nexus.core.AppEvent
import com.pinealctx.nexus.core.AppEventBus
import com.pinealctx.nexus.core.MessageData
import com.pinealctx.nexus.core.MessageSearchResultData
import com.pinealctx.nexus.core.managers.MessageManager
import com.pinealctx.nexus.core.managers.SearchManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepository @Inject constructor(
    private val messageManager: MessageManager,
    private val searchManager: SearchManager,
    private val appEventBus: AppEventBus
) {
    fun observeMessages(conversationId: String, limit: Int = 50): Flow<List<MessageData>> {
        return appEventBus.messagesUpdated()
            .filter { it.conversationId == conversationId }
            .onStart { emit(AppEvent.MessagesUpdated(conversationId)) }
            .map { messageManager.getMessages(conversationId, limit) }
            .flowOn(Dispatchers.IO)
    }

    fun getMessages(conversationId: String, limit: Int = 50, beforeId: Long? = null): List<MessageData> {
        return messageManager.getMessages(conversationId, limit, beforeId)
    }

    suspend fun sendText(conversationId: Long, text: String): Long {
        return messageManager.sendMessage(conversationId, text)
    }

    suspend fun sendImage(conversationId: Long, fileId: String, width: Int, height: Int): Long {
        return messageManager.sendImageMessage(conversationId, fileId, width, height)
    }

    suspend fun sendFile(conversationId: Long, fileId: String, name: String, size: Long): Long {
        return messageManager.sendFileMessage(conversationId, fileId, name, size)
    }

    suspend fun edit(conversationId: Long, messageId: Long, text: String) {
        messageManager.editMessage(conversationId, messageId, text)
    }

    suspend fun recall(conversationId: Long, messageId: Long) {
        messageManager.recallMessage(conversationId, messageId)
    }

    suspend fun delete(conversationId: Long, messageIds: List<Long>) {
        messageManager.deleteMessages(conversationId, messageIds)
    }

    fun search(query: String, conversationId: String? = null, limit: Int = 30, offset: Int = 0): List<MessageSearchResultData> {
        return searchManager.searchMessages(query, conversationId, limit, offset)
    }
}
