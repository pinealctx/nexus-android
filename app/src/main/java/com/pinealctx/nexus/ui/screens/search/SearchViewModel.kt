package com.pinealctx.nexus.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinealctx.nexus.core.ContactData
import com.pinealctx.nexus.core.MessageSearchResultData
import com.pinealctx.nexus.core.managers.ContactManager
import com.pinealctx.nexus.core.managers.SearchManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SearchTab { MESSAGES, USERS }

data class SearchUiState(
    val query: String = "",
    val activeTab: SearchTab = SearchTab.MESSAGES,
    val userResults: List<ContactData> = emptyList(),
    val messageResults: List<MessageSearchResultData> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val friendRequestSent: Boolean = false,
    val hasMoreMessages: Boolean = true
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchManager: SearchManager,
    private val contactManager: ContactManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun updateQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        searchJob?.cancel()

        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(
                userResults = emptyList(),
                messageResults = emptyList(),
                error = null,
                hasMoreMessages = true
            )
            return
        }

        searchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(300)
            performSearch(query)
        }
    }

    fun switchTab(tab: SearchTab) {
        _uiState.value = _uiState.value.copy(activeTab = tab)
        val query = _uiState.value.query
        if (query.isNotBlank()) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch(Dispatchers.IO) {
                performSearch(query)
            }
        }
    }

    fun loadMoreMessages() {
        val state = _uiState.value
        if (!state.hasMoreMessages || state.isLoading || state.query.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val more = searchManager.searchMessages(
                    query = state.query,
                    offset = state.messageResults.size
                )
                _uiState.value = state.copy(
                    messageResults = state.messageResults + more,
                    hasMoreMessages = more.size == 30
                )
            } catch (_: Exception) {}
        }
    }

    private suspend fun performSearch(query: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        try {
            when (_uiState.value.activeTab) {
                SearchTab.MESSAGES -> {
                    val results = searchManager.searchMessages(query = query)
                    _uiState.value = _uiState.value.copy(
                        messageResults = results,
                        isLoading = false,
                        hasMoreMessages = results.size == 30
                    )
                }
                SearchTab.USERS -> {
                    val results = contactManager.searchUsers(query)
                    _uiState.value = _uiState.value.copy(
                        userResults = results,
                        isLoading = false
                    )
                }
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                error = e.message ?: "Search failed",
                isLoading = false
            )
        }
    }

    fun sendFriendRequest(userId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                contactManager.sendFriendRequest(userId, "")
                _uiState.value = _uiState.value.copy(friendRequestSent = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }
}
