package com.pinealctx.nexus.ui.screens.contacts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pinealctx.nexus.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    onFriendRequestsClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    viewModel: ContactsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.contacts_title)) },
            actions = {
                IconButton(onClick = onSearchClick) {
                    Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.contacts_search))
                }
                IconButton(onClick = onFriendRequestsClick) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = stringResource(R.string.contacts_friend_requests))
                }
            }
        )

        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null -> {
                ContactsEmptyState(
                    title = stringResource(R.string.contacts_load_failed),
                    message = uiState.error ?: stringResource(R.string.error_unknown),
                    actionLabel = stringResource(R.string.friend_requests_retry),
                    onAction = { viewModel.refresh() }
                )
            }
            uiState.contacts.isEmpty() -> {
                ContactsEmptyState(
                    title = stringResource(R.string.contacts_empty),
                    message = stringResource(R.string.contacts_empty_desc),
                    actionLabel = stringResource(R.string.contacts_search),
                    onAction = onSearchClick
                )
            }
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.contacts) { contact ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    contact.nickname.ifEmpty { contact.username },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            supportingContent = {
                                Text(
                                    "@${contact.username}",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingContent = {
                                Surface(
                                    modifier = Modifier.size(44.dp),
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = (contact.nickname.firstOrNull()
                                                ?: contact.username.firstOrNull()
                                                ?: '?').toString(),
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }
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

@Composable
private fun ContactsEmptyState(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.Group,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        FilledTonalButton(onClick = onAction) {
            Text(actionLabel)
        }
    }
}
