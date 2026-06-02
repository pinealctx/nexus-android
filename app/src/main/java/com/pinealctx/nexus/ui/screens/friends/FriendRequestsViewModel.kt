package com.pinealctx.nexus.ui.screens.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinealctx.nexus.core.AppEventBus
import com.pinealctx.nexus.core.PendingRequestData
import com.pinealctx.nexus.core.managers.ContactManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FriendRequestsUiState(
    val requests: List<PendingRequestData> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class FriendRequestsViewModel @Inject constructor(
    private val contactManager: ContactManager,
    private val appEventBus: AppEventBus
) : ViewModel() {

    private val _uiState = MutableStateFlow(FriendRequestsUiState())
    val uiState: StateFlow<FriendRequestsUiState> = _uiState.asStateFlow()

    init {
        loadRequests()
        observeUpdates()
    }

    private fun observeUpdates() {
        appEventBus.contactsUpdated()
            .onEach { loadRequests() }
            .launchIn(viewModelScope)
    }

    fun loadRequests() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val requests = contactManager.getPendingRequests()
                _uiState.value = FriendRequestsUiState(requests = requests)
            } catch (e: Exception) {
                _uiState.value = FriendRequestsUiState(error = e.message)
            }
        }
    }

    fun acceptRequest(requestId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                contactManager.acceptFriendRequest(requestId)
                loadRequests()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun rejectRequest(requestId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                contactManager.rejectFriendRequest(requestId)
                loadRequests()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }
}
