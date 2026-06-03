package com.pinealctx.nexus.ui.screens.conversations

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinealctx.nexus.core.AppEventBus
import com.pinealctx.nexus.core.ConversationData
import com.pinealctx.nexus.core.NexusError
import com.pinealctx.nexus.data.repository.ConversationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import uniffi.nexus_ffi.ConnectionStatus
import javax.inject.Inject

data class ConversationListUiState(
    val conversations: List<ConversationData> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ConversationListViewModel @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val appEventBus: AppEventBus
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConversationListUiState())
    val uiState: StateFlow<ConversationListUiState> = _uiState.asStateFlow()

    init {
        loadConversations(fetchIfEmpty = true)
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
        appEventBus.connectionStatus
            .filter { it == ConnectionStatus.CONNECTED }
            .onEach { loadConversations(fetchIfEmpty = true) }
            .launchIn(viewModelScope)
    }

    private fun loadConversations(fetchIfEmpty: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                var conversations = conversationRepository.getConversations()
                Log.i("ConversationList", "Loaded local conversations: count=${conversations.size}")
                if (fetchIfEmpty && conversations.isEmpty()) {
                    try {
                        conversationRepository.fetchFromRemote()
                        conversations = conversationRepository.getConversations()
                        Log.i("ConversationList", "Fetched remote conversations: count=${conversations.size}")
                    } catch (e: Exception) {
                        if (e.requiresRelogin()) return@launch
                        Log.w("ConversationList", "Remote conversation fetch failed", e)
                    }
                }
                _uiState.value = ConversationListUiState(conversations = conversations)
            } catch (e: Exception) {
                if (e.requiresRelogin()) return@launch
                _uiState.value = ConversationListUiState(error = e.message)
            }
        }
    }

    fun refresh() {
        loadConversations()
    }

    private fun Exception.requiresRelogin(): Boolean {
        if (!NexusError.requiresRelogin(this)) return false
        appEventBus.emitForceLogout(message ?: "Authentication expired")
        return true
    }
}
