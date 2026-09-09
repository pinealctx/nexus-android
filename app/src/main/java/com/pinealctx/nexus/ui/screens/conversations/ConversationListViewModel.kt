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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ConversationListUiState(
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val loadMoreFailed: Boolean = false,
    val conversations: List<ConversationData> = emptyList(),
    val isLoading: Boolean = true,
    val isSyncing: Boolean = false,
    val isPullRefreshing: Boolean = false,
    val hasCompletedInitialLoad: Boolean = false,
    val error: String? = null
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class ConversationListViewModel @Inject constructor(
    private val conversationRepository: ConversationRepository,
    private val appEventBus: AppEventBus
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConversationListUiState())
    val uiState: StateFlow<ConversationListUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var scheduledRefreshJob: Job? = null
    private var pendingRemoteRefresh = false
    private var pendingPullRefresh = false
    private val visibleLimit = MutableStateFlow(50)
    private var remoteHasMore = true
    private var cachedHasMore = false
    private var nextBeforeTime: Long? = null
    private var firstPageLoaded = false

    init {
        observeCache()
        loadConversations(forceRemote = true)
        observeUpdates()
    }

    private fun observeCache() {
        visibleLimit.flatMapLatest { conversationRepository.observeConversations(it + 1) }
            .onEach { conversations ->
                cachedHasMore = conversations.size > visibleLimit.value
                Log.i("ConversationList", "Observed cached conversations: count=${conversations.size}")
                _uiState.value = _uiState.value.copy(
                    conversations = conversations.take(visibleLimit.value),
                    hasMore = cachedHasMore || remoteHasMore,
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
            pendingRemoteRefresh = pendingRemoteRefresh || forceRemote
            if (showPullIndicator) {
                pendingPullRefresh = true
                _uiState.value = _uiState.value.copy(isPullRefreshing = true)
            }
            return
        }

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
                val page = withContext(Dispatchers.IO) { conversationRepository.fetchPage() }
                if (!firstPageLoaded) {
                    nextBeforeTime = page.conversations.minOfOrNull { it.lastMessageTime }
                    remoteHasMore = page.hasMore && nextBeforeTime != null
                    firstPageLoaded = true
                }
                _uiState.value = _uiState.value.copy(
                    hasMore = cachedHasMore || remoteHasMore,
                    isLoading = _uiState.value.conversations.isEmpty() && page.conversations.isNotEmpty(),
                    isSyncing = false,
                    isPullRefreshing = false,
                    hasCompletedInitialLoad = true,
                    error = null
                )
            } catch (e: CancellationException) {
                throw e
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

    fun loadMore() {
        if (loadJob?.isActive == true || _uiState.value.isLoadingMore) return
        visibleLimit.value += 50
        if (!remoteHasMore) return
        if (!firstPageLoaded) { refresh(); return }
        _uiState.value = _uiState.value.copy(isLoadingMore = true, loadMoreFailed = false)
        loadJob = viewModelScope.launch {
            try {
                val cursor = nextBeforeTime
                val page = withContext(Dispatchers.IO) { conversationRepository.fetchPage(beforeTime = cursor) }
                val next = page.conversations.minOfOrNull { it.lastMessageTime }
                remoteHasMore = page.hasMore && next != null && (cursor == null || next < cursor)
                if (next != null) nextBeforeTime = next
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!e.requiresRelogin()) _uiState.value = _uiState.value.copy(loadMoreFailed = true)
            } finally {
                _uiState.value = _uiState.value.copy(isLoadingMore = false, hasMore = cachedHasMore || remoteHasMore)
                loadJob = null
                if (pendingRemoteRefresh) {
                    pendingRemoteRefresh = false
                    loadConversations(forceRemote = true, showPullIndicator = pendingPullRefresh)
                    pendingPullRefresh = false
                }
            }
        }
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
