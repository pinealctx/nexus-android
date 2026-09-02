package com.pinealctx.nexus.ui.screens.conversations

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pinealctx.nexus.R
import com.pinealctx.nexus.core.ConversationData
import com.pinealctx.nexus.ui.components.NexusAvatar
import com.pinealctx.nexus.ui.components.NexusAvatarBadge
import com.pinealctx.nexus.ui.components.NexusMainHeader
import com.shared.v1.ConversationType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
fun ConversationListScreen(
    onConversationClick: (String) -> Unit,
    onSearchClick: () -> Unit = {},
    viewModel: ConversationListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    ConversationListContent(
        uiState = uiState,
        onConversationClick = onConversationClick,
        onSearchClick = onSearchClick,
        onRefresh = viewModel::refresh
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConversationListContent(
    uiState: ConversationListUiState,
    onConversationClick: (String) -> Unit,
    onSearchClick: () -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        NexusMainHeader(
            title = stringResource(R.string.conversations_title),
            actions = {
                IconButton(
                    onClick = onRefresh,
                    enabled = !uiState.isLoading && !uiState.isPullRefreshing
                ) {
                    if (uiState.isSyncing && !uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.conversations_refresh),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        )

        SearchEntry(onClick = onSearchClick)

        AnimatedVisibility(visible = uiState.isSyncing && !uiState.isLoading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
            )
        }

        PullToRefreshBox(
            isRefreshing = uiState.isPullRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            when (resolveConversationListPhase(uiState)) {
                ConversationListPhase.LOADING -> ConversationSkeletonList()
                ConversationListPhase.ERROR -> ConversationEmptyState(
                    modifier = Modifier.fillMaxSize(),
                    title = stringResource(R.string.conversations_load_failed),
                    message = stringResource(R.string.conversations_load_failed_desc),
                    primaryActionLabel = stringResource(R.string.conversations_retry),
                    onPrimaryAction = onRefresh,
                    icon = {
                        Icon(
                            Icons.Outlined.CloudOff,
                            contentDescription = null,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                )
                ConversationListPhase.EMPTY -> ConversationEmptyState(
                    modifier = Modifier.fillMaxSize(),
                    title = stringResource(R.string.conversations_empty),
                    message = stringResource(R.string.conversations_empty_desc),
                    primaryActionLabel = stringResource(R.string.conversations_start_chat),
                    onPrimaryAction = onSearchClick,
                    secondaryActionLabel = stringResource(R.string.conversations_refresh),
                    onSecondaryAction = onRefresh,
                    icon = {
                        Icon(
                            Icons.AutoMirrored.Filled.Chat,
                            contentDescription = null,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                )
                ConversationListPhase.CONTENT -> ConversationList(
                    conversations = uiState.conversations,
                    refreshError = uiState.error,
                    onConversationClick = onConversationClick,
                    onRefresh = onRefresh
                )
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
            .height(46.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(9.dp))
            Text(
                text = stringResource(R.string.conversations_search_placeholder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ConversationSkeletonList() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = false,
        contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp)
    ) {
        items(7) { index ->
            ConversationSkeletonRow(index = index)
        }
    }
}

@Composable
private fun ConversationSkeletonRow(index: Int) {
    val placeholder = MaterialTheme.colorScheme.onSurface.copy(
        alpha = if (index % 2 == 0) 0.075f else 0.055f
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(modifier = Modifier.size(54.dp), shape = CircleShape, color = placeholder) {}
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(if (index % 3 == 0) 0.52f else 0.66f)
                    .height(15.dp),
                shape = RoundedCornerShape(8.dp),
                color = placeholder
            ) {}
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth(if (index % 2 == 0) 0.88f else 0.72f)
                    .height(12.dp),
                shape = RoundedCornerShape(8.dp),
                color = placeholder.copy(alpha = placeholder.alpha * 0.78f)
            ) {}
        }
        Spacer(modifier = Modifier.width(20.dp))
        Surface(
            modifier = Modifier
                .width(38.dp)
                .height(11.dp),
            shape = RoundedCornerShape(8.dp),
            color = placeholder.copy(alpha = placeholder.alpha * 0.72f)
        ) {}
    }
}

@Composable
private fun ConversationList(
    conversations: List<ConversationData>,
    refreshError: String?,
    onConversationClick: (String) -> Unit,
    onRefresh: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 2.dp, bottom = 12.dp)
    ) {
        if (refreshError != null) {
            item(key = "refresh-error") {
                RefreshErrorBanner(onRefresh = onRefresh)
            }
        }
        items(
            items = conversations,
            key = { it.conversationId }
        ) { conversation ->
            ConversationRow(
                conversation = conversation,
                onClick = { onConversationClick(conversation.conversationId) }
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 84.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)
            )
        }
    }
}

@Composable
private fun RefreshErrorBanner(onRefresh: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.70f),
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.CloudOff, contentDescription = null, modifier = Modifier.size(21.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.conversations_refresh_failed),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.conversations_refresh_failed_desc),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TextButton(onClick = onRefresh) {
                Text(stringResource(R.string.conversations_retry))
            }
        }
    }
}

@Composable
private fun ConversationRow(conversation: ConversationData, onClick: () -> Unit) {
    val title = conversation.displayTitle()
    val unread = conversation.unreadCount
    val hasUnread = unread > 0
    val rowColor = if (hasUnread) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.09f)
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NexusAvatar(
            id = conversation.avatarStableId(),
            name = title,
            avatarUrl = conversation.avatarUrl,
            size = 54.dp,
            badge = conversation.avatarBadge()
        )

        Spacer(modifier = Modifier.width(13.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = conversation.lastMessageTime.formatConversationTime(
                        yesterdayLabel = stringResource(R.string.conversations_yesterday)
                    ),
                    maxLines = 1,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (hasUnread) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Spacer(modifier = Modifier.height(5.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = conversation.localizedPreviewText(),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (conversation.isMuted) {
                    Spacer(modifier = Modifier.width(7.dp))
                    Icon(
                        Icons.Filled.NotificationsOff,
                        contentDescription = stringResource(R.string.conversations_muted),
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (unread > 0) {
                    Spacer(modifier = Modifier.width(7.dp))
                    UnreadBadge(unread = unread, muted = conversation.isMuted)
                }
            }
        }
    }
}

@Composable
private fun UnreadBadge(unread: Long, muted: Boolean) {
    if (muted) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f),
                    shape = CircleShape
                )
        )
    } else {
        Badge(
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
private fun ConversationEmptyState(
    modifier: Modifier,
    title: String,
    message: String,
    primaryActionLabel: String,
    onPrimaryAction: () -> Unit,
    icon: @Composable () -> Unit,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(68.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.70f),
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Box(contentAlignment = Alignment.Center) { icon() }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            modifier = Modifier.widthIn(max = 320.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(22.dp))
        Button(
            onClick = onPrimaryAction,
            modifier = Modifier
                .widthIn(min = 180.dp)
                .height(50.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(primaryActionLabel, fontWeight = FontWeight.SemiBold)
        }
        if (secondaryActionLabel != null && onSecondaryAction != null) {
            TextButton(onClick = onSecondaryAction) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(secondaryActionLabel)
            }
        }
    }
}

internal enum class ConversationListPhase {
    LOADING,
    ERROR,
    EMPTY,
    CONTENT
}

internal fun resolveConversationListPhase(state: ConversationListUiState): ConversationListPhase =
    when {
        state.conversations.isNotEmpty() -> ConversationListPhase.CONTENT
        state.isLoading || state.isSyncing || state.isPullRefreshing -> ConversationListPhase.LOADING
        state.error != null -> ConversationListPhase.ERROR
        else -> ConversationListPhase.EMPTY
    }

@Composable
private fun ConversationData.displayTitle(): String {
    displayName?.takeIf { it.isNotBlank() }?.let { return it }
    return when {
        peerId > 0 -> stringResource(R.string.conversations_user_fallback, peerId)
        isGroupConversation() -> stringResource(R.string.conversations_group_fallback, conversationId)
        else -> stringResource(R.string.conversations_unknown_fallback, conversationId.takeLast(6))
    }
}

@Composable
private fun ConversationData.localizedPreviewText(): String {
    val preview = lastMessageContent?.takeIf { it.isNotBlank() }
        ?: return stringResource(R.string.conversations_no_preview)
    return when {
        preview == "[Image]" -> stringResource(R.string.conversations_preview_image)
        preview == "[Audio]" -> stringResource(R.string.conversations_preview_audio)
        preview == "[Video]" -> stringResource(R.string.conversations_preview_video)
        preview == "[Card]" -> stringResource(R.string.conversations_preview_card)
        preview == "[Group update]" -> stringResource(R.string.conversations_preview_group_update)
        preview == "[Message recalled]" -> stringResource(R.string.conversations_preview_recalled)
        preview == "[Message]" -> stringResource(R.string.conversations_preview_message)
        preview.startsWith("[File] ") -> stringResource(
            R.string.conversations_preview_file,
            preview.removePrefix("[File] ")
        )
        else -> preview
    }
}

private fun ConversationData.avatarStableId(): Int =
    if (peerId > 0) peerId else conversationId.hashCode()

private fun ConversationData.avatarBadge(): NexusAvatarBadge? =
    if (isGroupConversation()) NexusAvatarBadge.Group else null

private fun ConversationData.isGroupConversation(): Boolean =
    conversationType == ConversationType.CONVERSATION_TYPE_GROUP

private fun formatUnread(unread: Long): String =
    if (unread > 99) "99+" else unread.toString()

private fun Long.formatConversationTime(yesterdayLabel: String): String =
    formatConversationTime(
        timestamp = this,
        now = System.currentTimeMillis(),
        zoneId = ZoneId.systemDefault(),
        locale = Locale.getDefault(),
        yesterdayLabel = yesterdayLabel
    )

internal fun formatConversationTime(
    timestamp: Long,
    now: Long,
    zoneId: ZoneId,
    locale: Locale,
    yesterdayLabel: String
): String {
    if (timestamp <= 0L) return ""

    val messageDateTime = Instant.ofEpochMilli(timestamp).atZone(zoneId)
    val nowDateTime = Instant.ofEpochMilli(now).atZone(zoneId)
    val messageDate = messageDateTime.toLocalDate()
    val today = nowDateTime.toLocalDate()
    val daysAgo = ChronoUnit.DAYS.between(messageDate, today)

    val pattern = when {
        messageDate == today -> "HH:mm"
        daysAgo == 1L -> return yesterdayLabel
        daysAgo in 2L..6L -> "EEE"
        messageDate.year == today.year -> "MM/dd"
        else -> "yyyy/MM/dd"
    }
    return messageDateTime.format(DateTimeFormatter.ofPattern(pattern, locale))
}
