package com.pinealctx.nexus.ui.screens.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pinealctx.nexus.R
import com.pinealctx.nexus.core.ContactData
import com.pinealctx.nexus.core.MessageSearchResultData
import com.pinealctx.nexus.ui.components.NexusAvatar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    initialTab: SearchTab = SearchTab.MESSAGES,
    onBack: () -> Unit,
    onNavigateToChat: (String, Long) -> Unit = { _, _ -> },
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val friendRequestSentMessage = stringResource(R.string.search_friend_request_sent)

    LaunchedEffect(initialTab) {
        viewModel.switchTab(initialTab)
    }

    LaunchedEffect(uiState.friendRequestSent) {
        if (uiState.friendRequestSent) {
            snackbarHostState.showSnackbar(friendRequestSentMessage)
            viewModel.consumeFriendRequestSent()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
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
            // Search input
            OutlinedTextField(
                value = uiState.query,
                onValueChange = { viewModel.updateQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.search_placeholder)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateQuery("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.search_clear))
                        }
                    }
                },
                singleLine = true
            )

            // Tab row
            TabRow(
                selectedTabIndex = uiState.activeTab.ordinal
            ) {
                Tab(
                    selected = uiState.activeTab == SearchTab.MESSAGES,
                    onClick = { viewModel.switchTab(SearchTab.MESSAGES) },
                    text = { Text(stringResource(R.string.search_tab_messages)) }
                )
                Tab(
                    selected = uiState.activeTab == SearchTab.USERS,
                    onClick = { viewModel.switchTab(SearchTab.USERS) },
                    text = { Text(stringResource(R.string.search_tab_users)) }
                )
            }

            // Content
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = uiState.error ?: stringResource(R.string.search_failed),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                else -> {
                    when (uiState.activeTab) {
                        SearchTab.MESSAGES -> MessageSearchResults(
                            results = uiState.messageResults,
                            query = uiState.query,
                            hasMore = uiState.hasMoreMessages,
                            onLoadMore = { viewModel.loadMoreMessages() },
                            onResultClick = onNavigateToChat
                        )
                        SearchTab.USERS -> UserSearchResults(
                            results = uiState.userResults,
                            query = uiState.query,
                            onAddFriend = { viewModel.sendFriendRequest(it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageSearchResults(
    results: List<MessageSearchResultData>,
    query: String,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    onResultClick: (String, Long) -> Unit
) {
    if (results.isEmpty() && query.isNotEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.search_no_messages),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    if (query.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.search_hint_messages),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val listState = rememberLazyListState()

    LaunchedEffect(listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index) {
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@LaunchedEffect
        if (lastVisible >= results.size - 5 && hasMore) {
            onLoadMore()
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()
    ) {
        items(results) { result ->
            MessageSearchResultItem(
                result = result,
                onClick = { onResultClick(result.conversationId, result.messageId) }
            )
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
        }
    }
}

@Composable
private fun MessageSearchResultItem(
    result: MessageSearchResultData,
    onClick: () -> Unit
) {
    val highlightedText = buildHighlightedText(result.textSnippet)
    val timeText = formatTimestamp(result.createdAt)

    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = {
            Text(
                text = highlightedText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                text = timeText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}

@Composable
private fun UserSearchResults(
    results: List<ContactData>,
    query: String,
    onAddFriend: (Int) -> Unit
) {
    if (results.isEmpty() && query.isNotEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.search_no_results),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    if (query.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.search_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(results, key = { it.userId }) { user ->
            SearchResultItem(
                user = user,
                onAddFriend = { onAddFriend(user.userId) }
            )
            HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
        }
    }
}

@Composable
private fun SearchResultItem(
    user: ContactData,
    onAddFriend: () -> Unit
) {
    ListItem(
        headlineContent = { Text(user.nickname.ifEmpty { user.username }) },
        supportingContent = { Text("@${user.username}") },
        leadingContent = {
            NexusAvatar(
                id = user.userId,
                name = user.nickname.ifEmpty { user.username },
                avatarUrl = user.avatarUrl,
                size = 40.dp
            )
        },
        trailingContent = {
            IconButton(onClick = onAddFriend) {
                Icon(
                    Icons.Filled.PersonAdd,
                    contentDescription = stringResource(R.string.search_add_friend),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    )
}

@Composable
private fun buildHighlightedText(snippet: String) = buildAnnotatedString {
    var i = 0
    while (i < snippet.length) {
        val start = snippet.indexOf('«', i)
        if (start == -1) {
            append(snippet.substring(i))
            break
        }
        append(snippet.substring(i, start))
        val end = snippet.indexOf('»', start)
        if (end == -1) {
            append(snippet.substring(start))
            break
        }
        pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
        append(snippet.substring(start + 1, end))
        pop()
        i = end + 1
    }
}

private fun formatTimestamp(millis: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(millis))
}
