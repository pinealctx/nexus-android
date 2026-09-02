package com.pinealctx.nexus.ui.screens.conversations

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinealctx.nexus.core.AppEventBus
import com.pinealctx.nexus.core.ConnectionStatus
import com.pinealctx.nexus.core.ConversationData
import com.pinealctx.nexus.core.NexusError
import com.pinealctx.nexus.data.repository.ConversationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ConversationListUiState(
    val conversations: List<ConversationData> = emptyList(),
    val isLoading: Boolean = true,
    val isSyncing: Boolean = false,
    val isPullRefreshing: Boolean = false,
    val hasCompletedInitialLoad: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ConversationListViewModel @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val appEventBus: AppEventBus
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConversationListUiState())
    val uiState: StateFlow<ConversationListUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var scheduledRefreshJob: Job? = null
    private var activeLoadIsRemote = false
    private var pendingRemoteRefresh = false
    private var pendingPullRefresh = false

    init {
        observeCache()
        loadConversations(forceRemote = true)
        observeUpdates()
    }

    private fun observeCache() {
        conversationRepository.observeConversations()
            .onEach { conversations ->
                Log.i("ConversationList", "Observed cached conversations: count=${conversations.size}")
                _uiState.value = _uiState.value.copy(
                    conversations = conversations,
                    isLoading = conversations.isEmpty() && _uiState.value.isLoading,
                    hasCompletedInitialLoad = conversations.isNotEmpty() ||
                        _uiState.value.hasCompletedInitialLoad
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
            .onEach { status ->
                if (status == ConnectionStatus.CONNECTED) {
                    scheduleRemoteRefresh()
                }
            }
            .launchIn(viewModelScope)
        appEventBus.coldStartCompleted()
            .onEach { scheduleRemoteRefresh() }
            .launchIn(viewModelScope)
    }

    private fun loadConversations(
        forceRemote: Boolean = false,
        showPullIndicator: Boolean = false
    ) {
        if (loadJob?.isActive == true) {
            pendingRemoteRefresh = pendingRemoteRefresh || (forceRemote && !activeLoadIsRemote)
            if (showPullIndicator) {
                pendingPullRefresh = true
                _uiState.value = _uiState.value.copy(isPullRefreshing = true)
            }
            return
        }

        activeLoadIsRemote = forceRemote
        loadJob = viewModelScope.launch {
            val currentState = _uiState.value
            val isInitialLoad = currentState.conversations.isEmpty() &&
                !currentState.hasCompletedInitialLoad
            _uiState.value = currentState.copy(
                isLoading = isInitialLoad,
                isSyncing = forceRemote,
                isPullRefreshing = showPullIndicator,
                error = null
            )
            try {
                val conversations = withContext(Dispatchers.IO) {
                    if (forceRemote) {
                        conversationRepository.fetchFromRemote().also {
                            Log.i("ConversationList", "Fetched remote conversations: count=${it.size}")
                        }
                    } else {
                        conversationRepository.getConversations().also {
                            Log.i("ConversationList", "Loaded conversations: count=${it.size}")
                        }
                    }
                }
                _uiState.value = _uiState.value.copy(
                    conversations = conversations,
                    isLoading = false,
                    isSyncing = false,
                    isPullRefreshing = false,
                    hasCompletedInitialLoad = true,
                    error = null
                )
            } catch (e: Exception) {
                if (e.requiresRelogin()) return@launch
                Log.w("ConversationList", "Conversation refresh failed", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isSyncing = false,
                    isPullRefreshing = false,
                    hasCompletedInitialLoad = true,
                    error = e.message ?: "Conversation refresh failed"
                )
            } finally {
                val shouldRefreshAgain = pendingRemoteRefresh
                val showPendingPullIndicator = pendingPullRefresh
                pendingRemoteRefresh = false
                pendingPullRefresh = false
                activeLoadIsRemote = false
                loadJob = null
                if (shouldRefreshAgain) {
                    loadConversations(
                        forceRemote = true,
                        showPullIndicator = showPendingPullIndicator
                    )
                }
            }
        }
    }

    fun refresh() {
        loadConversations(forceRemote = true, showPullIndicator = true)
    }

    private fun scheduleRemoteRefresh() {
        scheduledRefreshJob?.cancel()
        scheduledRefreshJob = viewModelScope.launch {
            delay(300)
            loadConversations(forceRemote = true)
        }
    }

    private fun Exception.requiresRelogin(): Boolean {
        if (!NexusError.requiresRelogin(this)) return false
        appEventBus.emitForceLogout(message ?: "Authentication expired")
        return true
    }
}
