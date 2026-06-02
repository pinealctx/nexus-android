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
    val messages: List<MessageData> = emptyList(),
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
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
                markCurrentConversationRead(messages)
                _uiState.value = ChatUiState(messages = messages)
            } catch (e: Exception) {
                _uiState.value = ChatUiState(error = e.message)
            }
        }
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

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isSending = true)
            try {
                val convId = conversationId.toLongOrNull() ?: return@launch
                messageManager.sendMessage(convId, text)
                _uiState.value = _uiState.value.copy(isSending = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSending = false, error = e.message)
            }
        }
    }

    fun loadMore() {
        val oldest = _uiState.value.messages.firstOrNull() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val older = messageManager.getMessages(conversationId, beforeId = oldest.messageId)
                _uiState.value = _uiState.value.copy(
                    messages = older + _uiState.value.messages
                )
            } catch (_: Exception) {}
        }
    }

    fun sendImageMessage(data: ByteArray, fileName: String, width: Int, height: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isSending = true)
            try {
                val convId = conversationId.toLongOrNull() ?: return@launch
                val file = mediaManager.uploadFile(data, fileName, "image/jpeg", 3)
                messageManager.sendImageMessage(convId, file.fileId, width, height)
                _uiState.value = _uiState.value.copy(isSending = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSending = false, error = e.message)
            }
        }
    }

    fun sendFileMessage(data: ByteArray, fileName: String, contentType: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isSending = true)
            try {
                val convId = conversationId.toLongOrNull() ?: return@launch
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
                messageManager.sendFileMessage(convId, file.fileId, fileName, file.size)
                _uiState.value = _uiState.value.copy(isSending = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSending = false, error = e.message)
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
