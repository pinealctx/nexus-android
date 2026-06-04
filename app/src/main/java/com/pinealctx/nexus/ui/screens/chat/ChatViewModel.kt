package com.pinealctx.nexus.ui.screens.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinealctx.nexus.core.AppEventBus
import com.pinealctx.nexus.core.MessageData
import com.pinealctx.nexus.core.MessageSearchResultData
import com.pinealctx.nexus.core.SyncManager
import com.pinealctx.nexus.core.managers.ConversationManager
import com.pinealctx.nexus.core.managers.MediaManager
import com.pinealctx.nexus.core.managers.MessageManager
import com.pinealctx.nexus.core.managers.SearchManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val messages: List<ChatMessageItem> = emptyList(),
    val currentUserId: Int = 0,
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val isLocatingMessage: Boolean = false,
    val scrollToMessageId: Long? = null,
    val pendingMessageActionId: Long? = null,
    val error: String? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val conversationManager: ConversationManager,
    private val messageManager: MessageManager,
    private val mediaManager: MediaManager,
    private val searchManager: SearchManager,
    private val appEventBus: AppEventBus,
    private val syncManager: SyncManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val conversationId: String = savedStateHandle["conversationId"] ?: ""
    val initialDraft: String = syncManager.getDraft(conversationId)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        syncManager.activeConversationId = conversationId
        loadMessages()
        observeUpdates()
    }

    override fun onCleared() {
        super.onCleared()
        if (syncManager.activeConversationId == conversationId) {
            syncManager.activeConversationId = null
        }
    }

    fun saveDraft(text: String) {
        syncManager.saveDraft(conversationId, text)
    }

    private fun observeUpdates() {
        appEventBus.messagesUpdated()
            .filter { it.conversationId == conversationId }
            .onEach { loadMessages() }
            .launchIn(viewModelScope)
    }

    private fun loadMessages() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val messages = messageManager.getMessages(conversationId)
                val localMessages = messageManager.getLocalMessages(conversationId)
                markCurrentConversationRead(messages)
                _uiState.value = ChatUiState(
                    messages = ChatMessageMerger.merge(messages, localMessages),
                    currentUserId = messageManager.currentUserId()
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    pendingMessageActionId = null,
                    error = e.message
                )
            }
        }
    }

    private fun refreshLocalMessages(isSending: Boolean = _uiState.value.isSending, error: String? = _uiState.value.error) {
        val remoteMessages = _uiState.value.messages
            .filterIsInstance<ChatMessageItem.Remote>()
            .map { it.data }
        val localMessages = messageManager.getLocalMessages(conversationId)
        _uiState.value = _uiState.value.copy(
            messages = ChatMessageMerger.merge(remoteMessages, localMessages),
            isSending = isSending,
            error = error
        )
    }

    private fun markCurrentConversationRead(messages: List<MessageData>) {
        val convId = conversationId.toLongOrNull() ?: return
        val conversation = conversationManager
            .getConversations()
            .firstOrNull { it.conversationId == conversationId }
        val latestMessageId = maxOf(
            conversation?.lastMessageId ?: 0L,
            messages.maxOfOrNull { it.messageId } ?: 0L
        )
        if (latestMessageId <= 0L || latestMessageId <= (conversation?.lastReadMessageId ?: 0L)) {
            return
        }
        conversationManager.markAsRead(convId, latestMessageId)
    }

    fun sendMessage(text: String, replyToMessageId: Long? = null) {
        if (text.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isSending = true)
            val convId = conversationId.toLongOrNull()
            if (convId == null) {
                _uiState.value = _uiState.value.copy(isSending = false)
                return@launch
            }
            val localMessage = messageManager.enqueueTextMessage(convId, text, replyToMessageId)
            refreshLocalMessages(isSending = true, error = null)
            try {
                messageManager.sendQueuedMessage(localMessage)
                refreshLocalMessages(isSending = false, error = null)
            } catch (e: Exception) {
                refreshLocalMessages(isSending = false, error = e.message)
            }
        }
    }

    fun retryMessage(clientMessageId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            messageManager.markLocalMessageSending(clientMessageId)
            refreshLocalMessages(isSending = true, error = null)
            runCatching {
                messageManager.retryLocalMessage(clientMessageId)
            }.onSuccess {
                refreshLocalMessages(isSending = false, error = null)
            }.onFailure { error ->
                refreshLocalMessages(isSending = false, error = error.message)
            }
        }
    }

    fun editMessage(messageId: Long, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val convId = conversationId.toLongOrNull() ?: return@launch
            _uiState.value = _uiState.value.copy(pendingMessageActionId = messageId, error = null)
            runCatching {
                messageManager.editMessage(convId, messageId, text)
            }.onSuccess {
                loadMessages()
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(pendingMessageActionId = null, error = error.message)
            }
        }
    }

    fun recallMessage(messageId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val convId = conversationId.toLongOrNull() ?: return@launch
            _uiState.value = _uiState.value.copy(pendingMessageActionId = messageId, error = null)
            runCatching {
                messageManager.recallMessage(convId, messageId)
            }.onSuccess {
                loadMessages()
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(pendingMessageActionId = null, error = error.message)
            }
        }
    }

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val convId = conversationId.toLongOrNull() ?: return@launch
            _uiState.value = _uiState.value.copy(pendingMessageActionId = messageId, error = null)
            runCatching {
                messageManager.deleteMessages(convId, listOf(messageId))
            }.onSuccess {
                loadMessages()
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(pendingMessageActionId = null, error = error.message)
            }
        }
    }

    fun revealMessage(messageId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            if (ChatMessageIndex.remoteIndexOf(_uiState.value.messages, messageId) >= 0) {
                _uiState.value = _uiState.value.copy(scrollToMessageId = messageId, error = null)
                return@launch
            }

            _uiState.value = _uiState.value.copy(isLocatingMessage = true, error = null)
            val found = loadUntilMessage(messageId)
            _uiState.value = _uiState.value.copy(
                isLocatingMessage = false,
                scrollToMessageId = if (found) messageId else null,
                error = if (found) null else "Message not found in loaded history"
            )
        }
    }

    fun consumeScrollTarget(messageId: Long) {
        if (_uiState.value.scrollToMessageId == messageId) {
            _uiState.value = _uiState.value.copy(scrollToMessageId = null)
        }
    }

    fun loadMore() {
        val oldest = _uiState.value.messages
            .filterIsInstance<ChatMessageItem.Remote>()
            .lastOrNull()
            ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val older = messageManager.getMessages(conversationId, beforeId = oldest.data.messageId)
                val remoteMessages = _uiState.value.messages
                    .filterIsInstance<ChatMessageItem.Remote>()
                    .map { it.data }
                val localMessages = messageManager.getLocalMessages(conversationId)
                _uiState.value = _uiState.value.copy(
                    messages = ChatMessageMerger.merge(remoteMessages + older, localMessages)
                )
            } catch (_: Exception) {}
        }
    }

    fun sendImageMessage(data: ByteArray, fileName: String, width: Int, height: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isSending = true)
            val convId = conversationId.toLongOrNull()
            if (convId == null) {
                _uiState.value = _uiState.value.copy(isSending = false)
                return@launch
            }
            try {
                val file = mediaManager.uploadFile(data, fileName, "image/jpeg", 3)
                val localMessage = messageManager.enqueueImageMessage(convId, file.fileId, width, height)
                refreshLocalMessages(isSending = true, error = null)
                messageManager.sendQueuedMessage(localMessage)
                refreshLocalMessages(isSending = false, error = null)
            } catch (e: Exception) {
                refreshLocalMessages(isSending = false, error = e.message)
            }
        }
    }

    private fun loadUntilMessage(messageId: Long, maxPages: Int = 20): Boolean {
        var remoteMessages = _uiState.value.messages
            .filterIsInstance<ChatMessageItem.Remote>()
            .map { it.data }
        if (remoteMessages.any { it.messageId == messageId }) return true

        repeat(maxPages) {
            val oldest = remoteMessages.minByOrNull { it.messageId } ?: return false
            val older = messageManager.getMessages(conversationId, beforeId = oldest.messageId)
            if (older.isEmpty()) return false

            remoteMessages = mergeRemoteMessages(remoteMessages, older)
            val localMessages = messageManager.getLocalMessages(conversationId)
            _uiState.value = _uiState.value.copy(
                messages = ChatMessageMerger.merge(remoteMessages, localMessages),
                currentUserId = messageManager.currentUserId()
            )
            if (remoteMessages.any { it.messageId == messageId }) return true
        }
        return false
    }

    private fun mergeRemoteMessages(
        current: List<MessageData>,
        incoming: List<MessageData>
    ): List<MessageData> {
        return (current + incoming)
            .distinctBy { it.messageId }
            .sortedByDescending { it.messageId }
    }

    fun sendFileMessage(data: ByteArray, fileName: String, contentType: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isSending = true)
            val convId = conversationId.toLongOrNull()
            if (convId == null) {
                _uiState.value = _uiState.value.copy(isSending = false)
                return@launch
            }
            try {
                val file = if (data.size <= 5 * 1024 * 1024) {
                    mediaManager.uploadFile(data, fileName, contentType, 3)
                } else {
                    val session = mediaManager.initUpload(fileName, contentType, data.size.toLong())
                    val chunkSize = 5 * 1024 * 1024
                    var offset = session.uploaded.toInt()
                    while (offset < data.size) {
                        val end = minOf(offset + chunkSize, data.size)
                        val chunk = data.copyOfRange(offset, end)
                        mediaManager.uploadChunk(session.sessionId, chunk, offset.toLong())
                        offset = end
                    }
                    mediaManager.completeUpload(session.sessionId)
                }
                val localMessage = messageManager.enqueueFileMessage(
                    conversationId = convId,
                    fileId = file.fileId,
                    name = fileName,
                    size = file.size,
                    mimeType = contentType
                )
                refreshLocalMessages(isSending = true, error = null)
                messageManager.sendQueuedMessage(localMessage)
                refreshLocalMessages(isSending = false, error = null)
            } catch (e: Exception) {
                refreshLocalMessages(isSending = false, error = e.message)
            }
        }
    }

    private var searchJob: Job? = null

    fun searchInConversation(query: String, onResults: (List<MessageSearchResultData>) -> Unit) {
        searchJob?.cancel()
        if (query.isBlank()) {
            onResults(emptyList())
            return
        }
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(300)
            try {
                val results = searchManager.searchMessages(
                    query = query,
                    conversationId = conversationId
                )
                onResults(results)
            } catch (_: Exception) {
                onResults(emptyList())
            }
        }
    }
}
