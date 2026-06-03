package com.pinealctx.nexus.ui.screens.conversations

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pinealctx.nexus.R
import com.pinealctx.nexus.core.ConversationData
import com.pinealctx.nexus.ui.components.NexusMainHeader
import com.pinealctx.nexus.ui.components.NexusAvatar
import com.pinealctx.nexus.ui.components.NexusAvatarBadge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ConversationListScreen(
    onConversationClick: (String) -> Unit,
    onSearchClick: () -> Unit = {},
    viewModel: ConversationListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        NexusMainHeader(
            title = stringResource(R.string.conversations_title)
        )

        SearchEntry(onClick = onSearchClick)

        when {
            uiState.isLoading && uiState.conversations.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null -> {
                EmptyConversationState(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    title = stringResource(R.string.conversations_load_failed),
                    message = uiState.error ?: stringResource(R.string.error_unknown),
                    actionLabel = stringResource(R.string.friend_requests_retry),
                    onAction = { viewModel.refresh() }
                )
            }
            uiState.conversations.isEmpty() -> {
                EmptyConversationState(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    title = stringResource(R.string.conversations_empty),
                    message = stringResource(R.string.conversations_empty_desc),
                    actionLabel = stringResource(R.string.search_title),
                    onAction = onSearchClick
                )
            }
            else -> {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(
                        items = uiState.conversations,
                        key = { it.conversationId }
                    ) { conversation ->
                        ConversationRow(
                            conversation = conversation,
                            onClick = { onConversationClick(conversation.conversationId) }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 76.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchEntry(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(40.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.conversations_search_placeholder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ConversationRow(conversation: ConversationData, onClick: () -> Unit) {
    val title = conversation.displayTitle()
    val unread = conversation.unreadCount

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NexusAvatar(
            id = conversation.avatarStableId(),
            name = title,
            avatarUrl = conversation.avatarUrl,
            size = 48.dp,
            badge = conversation.avatarBadge()
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (conversation.isMuted) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.NotificationsOff,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = conversation.previewText(stringResource(R.string.conversations_no_preview)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(
            modifier = Modifier.width(48.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = conversation.lastMessageTime.formatConversationTime(),
                maxLines = 1,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            UnreadBadge(unread = unread, muted = conversation.isMuted)
        }
    }
}

@Composable
private fun UnreadBadge(unread: Long, muted: Boolean) {
    when {
        unread <= 0 -> Spacer(modifier = Modifier.height(18.dp))
        muted -> Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                    shape = RoundedCornerShape(50)
                )
        )
        else -> Badge(
            containerColor = Color(0xFFEF4444),
            contentColor = Color.White
        ) {
            Text(
                text = formatUnread(unread),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
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
        Surface(
            modifier = Modifier.size(56.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.AutoMirrored.Filled.Chat,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAction, shape = RoundedCornerShape(8.dp)) {
            Text(actionLabel)
        }
    }
}

private fun ConversationData.displayTitle(): String {
    displayName?.takeIf { it.isNotBlank() }?.let { return it }
    return when {
        peerId > 0 -> "User #$peerId"
        isGroupConversation() -> "Group #$conversationId"
        else -> "#${conversationId.takeLast(6)}"
    }
}

private fun ConversationData.previewText(fallback: String): String {
    return lastMessageContent?.takeIf { it.isNotBlank() } ?: fallback
}

private fun ConversationData.avatarStableId(): Int {
    return if (peerId > 0) peerId else conversationId.hashCode()
}

private fun ConversationData.avatarBadge(): NexusAvatarBadge? {
    return if (isGroupConversation()) NexusAvatarBadge.Group else null
}

private fun ConversationData.isGroupConversation(): Boolean {
    return conversationType > 1
}

private fun formatUnread(unread: Long): String {
    return if (unread > 99) "99+" else unread.toString()
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
