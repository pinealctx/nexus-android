package com.pinealctx.nexus.ui.screens.groups

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinealctx.nexus.core.GroupData
import com.pinealctx.nexus.core.GroupMemberData
import com.pinealctx.nexus.core.managers.GroupManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupDetailUiState(
    val group: GroupData? = null,
    val members: List<GroupMemberData> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val leftGroup: Boolean = false
)

@HiltViewModel
class GroupDetailViewModel @Inject constructor(
    private val groupManager: GroupManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val groupId: Int = savedStateHandle.get<Int>("groupId") ?: 0

    private val _uiState = MutableStateFlow(GroupDetailUiState())
    val uiState: StateFlow<GroupDetailUiState> = _uiState.asStateFlow()

    init {
        loadGroupDetail()
    }

    fun loadGroupDetail() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val group = groupManager.getGroupInfo(groupId)
                val members = groupManager.getGroupMembers(groupId)
                _uiState.value = GroupDetailUiState(group = group, members = members)
            } catch (e: Exception) {
                _uiState.value = GroupDetailUiState(error = e.message)
            }
        }
    }

    fun leaveGroup() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                groupManager.leaveGroup(groupId)
                _uiState.value = _uiState.value.copy(leftGroup = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }
}
