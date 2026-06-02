package com.pinealctx.nexus.ui.screens.conversations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinealctx.nexus.core.AppEventBus
import com.pinealctx.nexus.core.ConversationData
import com.pinealctx.nexus.core.managers.ConversationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConversationListUiState(
    val conversations: List<ConversationData> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ConversationListViewModel @Inject constructor(
    private val conversationManager: ConversationManager,
    private val appEventBus: AppEventBus
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConversationListUiState())
    val uiState: StateFlow<ConversationListUiState> = _uiState.asStateFlow()

    init {
        loadConversations()
        observeUpdates()
    }

    private fun observeUpdates() {
        appEventBus.conversationsUpdated()
            .onEach { loadConversations() }
            .launchIn(viewModelScope)
        appEventBus.messagesUpdated()
            .onEach { loadConversations() }
            .launchIn(viewModelScope)
        appEventBus.contactsUpdated()
            .onEach { loadConversations() }
            .launchIn(viewModelScope)
    }

    private fun loadConversations() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val conversations = conversationManager.getConversations()
                _uiState.value = ConversationListUiState(conversations = conversations)
            } catch (e: Exception) {
                _uiState.value = ConversationListUiState(error = e.message)
            }
        }
    }

    fun refresh() {
        loadConversations()
    }
}
