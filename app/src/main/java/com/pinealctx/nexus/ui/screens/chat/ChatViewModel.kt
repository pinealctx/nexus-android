package com.pinealctx.nexus.ui.screens.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinealctx.nexus.core.EventBridge
import com.pinealctx.nexus.core.MessageData
import com.pinealctx.nexus.core.NexusCoreWrapper
import com.pinealctx.nexus.core.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
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
    private val core: NexusCoreWrapper,
    private val eventBridge: EventBridge,
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
        eventBridge.messagesUpdated
            .filter { it == conversationId }
            .onEach { loadMessages() }
            .launchIn(viewModelScope)
    }

    private fun loadMessages() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val messages = core.getMessages(conversationId)
                _uiState.value = ChatUiState(messages = messages)
            } catch (e: Exception) {
                _uiState.value = ChatUiState(error = e.message)
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isSending = true)
            try {
                val convId = conversationId.toLongOrNull() ?: return@launch
                core.sendMessage(convId, text)
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
                val older = core.getMessages(conversationId, beforeId = oldest.messageId)
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
                val file = core.uploadFile(data, fileName, "image/jpeg", 3)
                core.sendImageMessage(convId, file.fileId, width, height)
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
                    core.uploadFile(data, fileName, contentType, 3)
                } else {
                    val session = core.initUpload(fileName, contentType, data.size.toLong())
                    val chunkSize = 5 * 1024 * 1024
                    var offset = session.uploaded.toInt()
                    while (offset < data.size) {
                        val end = minOf(offset + chunkSize, data.size)
                        val chunk = data.copyOfRange(offset, end)
                        core.uploadChunk(session.sessionId, chunk, offset.toLong())
                        offset = end
                    }
                    core.completeUpload(session.sessionId)
                }
                core.sendFileMessage(convId, file.fileId, fileName, file.size)
                _uiState.value = _uiState.value.copy(isSending = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSending = false, error = e.message)
            }
        }
    }
}
