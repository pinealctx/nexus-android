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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import com.pinealctx.nexus.core.ConnectionStatus
import javax.inject.Inject

data class ConversationListUiState(
    val conversations: List<ConversationData> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class ConversationListViewModel @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val appEventBus: AppEventBus
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConversationListUiState())
    val uiState: StateFlow<ConversationListUiState> = _uiState.asStateFlow()
    private var refreshJob: Job? = null

    init {
        observeCache()
        loadConversations(fetchIfEmpty = true)
        observeUpdates()
    }

    private fun observeCache() {
        conversationRepository.observeConversations()
            .onEach { conversations ->
                Log.i("ConversationList", "Observed cached conversations: count=${conversations.size}")
                _uiState.value = _uiState.value.copy(
                    conversations = conversations,
                    isLoading = conversations.isEmpty() && _uiState.value.isLoading
                )
            }
            .launchIn(viewModelScope)
    }

    private fun observeUpdates() {
        appEventBus.conversationsUpdated()
            .onEach { scheduleRemoteRefresh() }
            .launchIn(viewModelScope)
        appEventBus.messagesUpdated()
            .onEach { scheduleRemoteRefresh() }
            .launchIn(viewModelScope)
        appEventBus.contactsUpdated()
            .onEach { scheduleRemoteRefresh() }
            .launchIn(viewModelScope)
        appEventBus.connectionStatus
            .filter { it == ConnectionStatus.CONNECTED }
            .onEach { scheduleRemoteRefresh() }
            .launchIn(viewModelScope)
        appEventBus.coldStartCompleted()
            .onEach { loadConversations() }
            .launchIn(viewModelScope)
    }

    private fun loadConversations(fetchIfEmpty: Boolean = false, forceRemote: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(
                isLoading = _uiState.value.conversations.isEmpty(),
                error = null
            )
            try {
                var conversations = conversationRepository.getConversations()
                Log.i("ConversationList", "Loaded local conversations: count=${conversations.size}")
                if (forceRemote || (fetchIfEmpty && conversations.isEmpty())) {
                    try {
                        conversations = conversationRepository.fetchFromRemote()
                        Log.i("ConversationList", "Fetched remote conversations: count=${conversations.size}")
                    } catch (e: Exception) {
                        if (e.requiresRelogin()) return@launch
                        Log.w("ConversationList", "Remote conversation fetch failed", e)
                        if (conversations.isEmpty()) throw e
                    }
                }
                _uiState.value = _uiState.value.copy(
                    conversations = conversations,
                    isLoading = false,
                    error = null
                )
            } catch (e: Exception) {
                if (e.requiresRelogin()) return@launch
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun refresh() {
        loadConversations(forceRemote = true)
    }

    private fun scheduleRemoteRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            delay(250)
            loadConversations(forceRemote = true)
        }
    }

    private fun Exception.requiresRelogin(): Boolean {
        if (!NexusError.requiresRelogin(this)) return false
        appEventBus.emitForceLogout(message ?: "Authentication expired")
        return true
    }
}
