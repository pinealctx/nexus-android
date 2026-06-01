package com.pinealctx.nexus.ui.screens.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinealctx.nexus.core.ContactData
import com.pinealctx.nexus.core.managers.ContactManager
import com.pinealctx.nexus.core.managers.GroupManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CreateGroupUiState(
    val contacts: List<ContactData> = emptyList(),
    val selectedIds: Set<Int> = emptySet(),
    val groupName: String = "",
    val isCreating: Boolean = false,
    val createdGroupId: Int? = null,
    val error: String? = null
)

@HiltViewModel
class CreateGroupViewModel @Inject constructor(
    private val contactManager: ContactManager,
    private val groupManager: GroupManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateGroupUiState())
    val uiState: StateFlow<CreateGroupUiState> = _uiState.asStateFlow()

    init {
        loadContacts()
    }

    private fun loadContacts() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val contacts = contactManager.getContacts()
                _uiState.value = _uiState.value.copy(contacts = contacts)
            } catch (_: Exception) {}
        }
    }

    fun setGroupName(name: String) {
        _uiState.value = _uiState.value.copy(groupName = name)
    }

    fun toggleMember(userId: Int) {
        val current = _uiState.value.selectedIds
        _uiState.value = _uiState.value.copy(
            selectedIds = if (userId in current) current - userId else current + userId
        )
    }

    fun createGroup() {
        val state = _uiState.value
        if (state.groupName.isBlank() || state.selectedIds.size < 2) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isCreating = true, error = null)
            try {
                val groupId = groupManager.createGroup(state.groupName, state.selectedIds.toList())
                _uiState.value = _uiState.value.copy(isCreating = false, createdGroupId = groupId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isCreating = false, error = e.message)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupScreen(
    onBack: () -> Unit,
    onGroupCreated: (Int) -> Unit,
    viewModel: CreateGroupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.createdGroupId) {
        uiState.createdGroupId?.let { onGroupCreated(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Group") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.createGroup() },
                        enabled = uiState.groupName.isNotBlank() && uiState.selectedIds.size >= 2 && !uiState.isCreating
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = "Create")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = uiState.groupName,
                onValueChange = { viewModel.setGroupName(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                label = { Text("Group Name") },
                singleLine = true
            )

            Text(
                text = "Select members (${uiState.selectedIds.size} selected, min 2)",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(uiState.contacts) { contact ->
                    val isSelected = contact.userId in uiState.selectedIds
                    ListItem(
                        headlineContent = { Text(contact.nickname.ifEmpty { contact.username }) },
                        supportingContent = { Text("@${contact.username}") },
                        leadingContent = {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { viewModel.toggleMember(contact.userId) }
                            )
                        },
                        modifier = Modifier.clickable { viewModel.toggleMember(contact.userId) }
                    )
                }
            }

            if (uiState.error != null) {
                Snackbar(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(uiState.error ?: "")
                }
            }
        }
    }
}
