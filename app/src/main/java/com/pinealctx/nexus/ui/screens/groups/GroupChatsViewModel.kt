package com.pinealctx.nexus.ui.screens.groups

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinealctx.nexus.core.AppEventBus
import com.pinealctx.nexus.core.GroupData
import com.pinealctx.nexus.core.managers.GroupManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import uniffi.nexus_ffi.ConnectionStatus

data class GroupChatsUiState(
    val groups: List<GroupData> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class GroupChatsViewModel @Inject constructor(
    private val groupManager: GroupManager,
    private val appEventBus: AppEventBus
) : ViewModel() {

    private val _uiState = MutableStateFlow(GroupChatsUiState())
    val uiState: StateFlow<GroupChatsUiState> = _uiState.asStateFlow()

    init {
        loadGroups(fetchIfEmpty = true)
        observeUpdates()
    }

    private fun observeUpdates() {
        appEventBus.conversationsUpdated()
            .onEach { loadGroups() }
            .launchIn(viewModelScope)
        appEventBus.connectionStatus
            .filter { it == ConnectionStatus.CONNECTED }
            .onEach { loadGroups(fetchIfEmpty = true) }
            .launchIn(viewModelScope)
    }

    private fun loadGroups(fetchIfEmpty: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                var groups = groupManager.listGroups()
                if (fetchIfEmpty && groups.isEmpty()) {
                    groupManager.fetchGroups()
                    groups = groupManager.listGroups()
                }
                Log.i("GroupChats", "Loaded groups: count=${groups.size}")
                _uiState.value = GroupChatsUiState(groups = groups)
            } catch (e: Exception) {
                _uiState.value = GroupChatsUiState(error = e.message)
            }
        }
    }

    fun refresh() {
        loadGroups(fetchIfEmpty = true)
    }
}
