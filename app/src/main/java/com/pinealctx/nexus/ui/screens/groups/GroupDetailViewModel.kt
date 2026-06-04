package com.pinealctx.nexus.ui.screens.groups

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinealctx.nexus.core.ContactData
import com.pinealctx.nexus.core.GroupData
import com.pinealctx.nexus.core.GroupMemberData
import com.pinealctx.nexus.core.MediaFileData
import com.pinealctx.nexus.core.SecureStorage
import com.pinealctx.nexus.core.managers.ContactManager
import com.pinealctx.nexus.core.managers.GroupManager
import com.pinealctx.nexus.core.managers.MediaManager
import com.shared.v1.MediaPurpose
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
    val contacts: List<ContactData> = emptyList(),
    val currentUserId: Int = 0,
    val isLoading: Boolean = false,
    val isInviting: Boolean = false,
    val isSavingName: Boolean = false,
    val isSavingDescription: Boolean = false,
    val isSavingAvatar: Boolean = false,
    val isExiting: Boolean = false,
    val removingMemberIds: Set<Int> = emptySet(),
    val error: String? = null,
    val leftGroup: Boolean = false
)

@HiltViewModel
class GroupDetailViewModel @Inject constructor(
    private val groupManager: GroupManager,
    private val contactManager: ContactManager,
    private val mediaManager: MediaManager,
    private val secureStorage: SecureStorage,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val groupId: Int = savedStateHandle.get<Int>("groupId") ?: 0

    private val _uiState = MutableStateFlow(
        GroupDetailUiState(currentUserId = secureStorage.getUserId())
    )
    val uiState: StateFlow<GroupDetailUiState> = _uiState.asStateFlow()

    init {
        loadGroupDetail()
    }

    fun loadGroupDetail() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val group = groupManager.getGroupInfo(groupId)
                val members = groupManager.getGroupMembers(groupId)
                val contacts = if (GroupDetailActionPolicy.canInviteMembers(group, _uiState.value.currentUserId)) {
                    contactManager.getContacts()
                } else {
                    emptyList()
                }
                _uiState.value = _uiState.value.copy(
                    group = group,
                    members = members,
                    contacts = contacts,
                    isLoading = false,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun inviteMembers(memberIds: List<Int>) {
        val uniqueMemberIds = memberIds.distinct()
        if (uniqueMemberIds.isEmpty()) return
        if (!GroupDetailActionPolicy.canInviteMembers(_uiState.value.group, _uiState.value.currentUserId)) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isInviting = true, error = null)
            try {
                groupManager.inviteMembers(groupId, uniqueMemberIds)
                _uiState.value = _uiState.value.copy(
                    members = groupManager.getGroupMembers(groupId),
                    isInviting = false,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isInviting = false, error = e.message)
            }
        }
    }

    fun removeMember(member: GroupMemberData) {
        if (!GroupDetailActionPolicy.canRemoveMember(_uiState.value.group, _uiState.value.currentUserId, member)) return
        if (member.userId in _uiState.value.removingMemberIds) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(
                removingMemberIds = _uiState.value.removingMemberIds + member.userId,
                error = null
            )
            try {
                groupManager.removeMember(groupId, member.userId)
                _uiState.value = _uiState.value.copy(
                    members = _uiState.value.members.filterNot { it.userId == member.userId },
                    removingMemberIds = _uiState.value.removingMemberIds - member.userId,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    removingMemberIds = _uiState.value.removingMemberIds - member.userId,
                    error = e.message
                )
            }
        }
    }

    fun updateGroupName(name: String) {
        val trimmedName = name.trim()
        val group = _uiState.value.group ?: return
        if (trimmedName.isEmpty() || trimmedName == group.name) return
        if (!GroupDetailActionPolicy.canEditGroupInfo(group, _uiState.value.currentUserId)) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isSavingName = true, error = null)
            try {
                groupManager.updateGroupName(groupId, trimmedName)
                _uiState.value = _uiState.value.copy(
                    group = group.copy(name = trimmedName),
                    isSavingName = false,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSavingName = false, error = e.message)
            }
        }
    }

    fun updateGroupDescription(description: String) {
        val trimmedDescription = description.trim()
        val group = _uiState.value.group ?: return
        if (trimmedDescription == group.description) return
        if (!GroupDetailActionPolicy.canEditGroupInfo(group, _uiState.value.currentUserId)) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isSavingDescription = true, error = null)
            try {
                groupManager.updateGroupDescription(groupId, trimmedDescription)
                _uiState.value = _uiState.value.copy(
                    group = group.copy(description = trimmedDescription),
                    isSavingDescription = false,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSavingDescription = false, error = e.message)
            }
        }
    }

    fun updateGroupAvatar(data: ByteArray, fileName: String, contentType: String) {
        val group = _uiState.value.group ?: return
        if (data.isEmpty()) return
        if (!GroupDetailActionPolicy.canEditGroupInfo(group, _uiState.value.currentUserId)) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isSavingAvatar = true, error = null)
            try {
                val file = uploadGroupAvatar(data, fileName, contentType)
                val avatarUrl = file.publicUrl.ifBlank { file.fileId }
                groupManager.updateGroupAvatar(groupId, avatarUrl)
                _uiState.value = _uiState.value.copy(
                    group = group.copy(avatarUrl = avatarUrl),
                    isSavingAvatar = false,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSavingAvatar = false, error = e.message)
            }
        }
    }

    fun exitGroup() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isExiting = true, error = null)
            try {
                when (GroupDetailActionPolicy.exitAction(_uiState.value.group, _uiState.value.currentUserId)) {
                    GroupExitAction.DISSOLVE -> groupManager.dissolveGroup(groupId)
                    GroupExitAction.LEAVE -> groupManager.leaveGroup(groupId)
                }
                _uiState.value = _uiState.value.copy(isExiting = false, leftGroup = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isExiting = false, error = e.message)
            }
        }
    }

    private fun uploadGroupAvatar(data: ByteArray, fileName: String, contentType: String): MediaFileData {
        return mediaManager.uploadFile(
            data = data,
            fileName = fileName,
            contentType = contentType,
            purpose = MediaPurpose.MEDIA_PURPOSE_GROUP_AVATAR.number
        )
    }
}
