package com.pinealctx.nexus.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinealctx.nexus.core.ContactData
import com.pinealctx.nexus.core.NexusCoreWrapper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val results: List<ContactData> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val friendRequestSent: Boolean = false
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val core: NexusCoreWrapper
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun updateQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        searchJob?.cancel()

        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(results = emptyList(), error = null)
            return
        }

        // Debounce search by 300ms
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(300)
            performSearch(query)
        }
    }

    private suspend fun performSearch(query: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        try {
            val results = core.searchUsers(query)
            _uiState.value = _uiState.value.copy(results = results, isLoading = false)
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
                core.sendFriendRequest(userId, "")
                _uiState.value = _uiState.value.copy(friendRequestSent = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }
}
