package com.pinealctx.nexus.ui.screens.conversations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
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
import com.pinealctx.nexus.core.ConversationData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    onConversationClick: (String) -> Unit,
    onSearchClick: () -> Unit = {},
    viewModel: ConversationListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.conversations_title)) },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search_title))
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading && uiState.conversations.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null -> {
                EmptyConversationState(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    title = stringResource(R.string.conversations_load_failed),
                    message = uiState.error ?: stringResource(R.string.error_unknown),
                    actionLabel = stringResource(R.string.friend_requests_retry),
                    onAction = { viewModel.refresh() }
                )
            }
            uiState.conversations.isEmpty() -> {
                EmptyConversationState(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    title = stringResource(R.string.conversations_empty),
                    message = stringResource(R.string.conversations_empty_desc),
                    actionLabel = stringResource(R.string.search_title),
                    onAction = onSearchClick
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding)
                ) {
                    items(uiState.conversations) { conversation ->
                        ConversationItem(
                            conversation = conversation,
                            onClick = { onConversationClick(conversation.conversationId) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
fun ConversationItem(conversation: ConversationData, onClick: () -> Unit) {
    ListItem(
        leadingContent = {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = conversation.displayInitial(),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        },
        headlineContent = {
            Text(
                text = conversation.displayTitle(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                text = conversation.lastMessagePreview?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.conversations_no_preview),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = conversation.lastMessageTime.formatConversationTime(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (conversation.unreadCount > 0) {
                    Spacer(Modifier.height(8.dp))
                    Badge { Text(conversation.unreadCount.coerceAtMost(99).toString()) }
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun EmptyConversationState(
    modifier: Modifier,
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.AutoMirrored.Filled.Chat,
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

private fun ConversationData.displayTitle(): String {
    displayName?.takeIf { it.isNotBlank() }?.let { return it }
    return when {
        peerId > 0 -> "User #$peerId"
        conversationType > 1 -> "Group #$conversationId"
        else -> "#${conversationId.takeLast(6)}"
    }
}

private fun ConversationData.displayInitial(): String {
    return displayTitle().firstOrNull()?.uppercase() ?: "#"
}

private fun Long.formatConversationTime(): String {
    if (this <= 0) return ""
    val now = System.currentTimeMillis()
    val format = if (now - this < 24 * 60 * 60 * 1000L) {
        SimpleDateFormat("HH:mm", Locale.getDefault())
    } else {
        SimpleDateFormat("MM/dd", Locale.getDefault())
    }
    return format.format(Date(this))
}
