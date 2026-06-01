package com.pinealctx.nexus.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinealctx.nexus.R
import com.pinealctx.nexus.core.managers.ContactManager
import com.pinealctx.nexus.core.managers.UserManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BlockedUsersUiState(
    val users: List<BlockedUserInfo> = emptyList(),
    val isLoading: Boolean = false
)

data class BlockedUserInfo(
    val userId: Int,
    val nickname: String,
    val username: String
)

@HiltViewModel
class BlockedUsersViewModel @Inject constructor(
    private val contactManager: ContactManager,
    private val userManager: UserManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(BlockedUsersUiState())
    val uiState: StateFlow<BlockedUsersUiState> = _uiState.asStateFlow()

    init {
        loadBlockedUsers()
    }

    private fun loadBlockedUsers() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val userIds = contactManager.getBlockedUsers()
                if (userIds.isEmpty()) {
                    _uiState.value = BlockedUsersUiState()
                    return@launch
                }
                val infos = userManager.batchGetUserInfo(userIds)
                val infoMap = infos.associateBy { it.userId }
                val users = userIds.map { id ->
                    val info = infoMap[id]
                    BlockedUserInfo(
                        userId = id,
                        nickname = info?.nickname ?: "",
                        username = info?.username ?: "user_$id"
                    )
                }
                _uiState.value = BlockedUsersUiState(users = users)
            } catch (_: Exception) {
                _uiState.value = BlockedUsersUiState()
            }
        }
    }

    fun unblock(userId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                contactManager.unblockUser(userId)
                loadBlockedUsers()
            } catch (_: Exception) {}
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedUsersScreen(
    onBack: () -> Unit,
    viewModel: BlockedUsersViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.blocked_users_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.users.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.blocked_users_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                    items(uiState.users) { user ->
                        ListItem(
                            headlineContent = { Text(user.nickname.ifEmpty { user.username }) },
                            supportingContent = { Text("@${user.username}") },
                            leadingContent = {
                                Surface(
                                    modifier = Modifier.size(40.dp),
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = (user.nickname.firstOrNull()
                                                ?: user.username.firstOrNull()
                                                ?: '?').toString(),
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }
                                }
                            },
                            trailingContent = {
                                TextButton(onClick = { viewModel.unblock(user.userId) }) {
                                    Text(
                                        stringResource(R.string.blocked_users_unblock),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                    }
                }
            }
        }
    }
}
