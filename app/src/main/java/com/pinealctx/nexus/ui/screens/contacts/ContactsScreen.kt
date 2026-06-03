package com.pinealctx.nexus.ui.screens.contacts

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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.pinealctx.nexus.core.ContactData
import com.pinealctx.nexus.core.GroupData
import com.pinealctx.nexus.ui.components.NexusAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    onFriendRequestsClick: () -> Unit = {},
    onGroupChatsClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    viewModel: ContactsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.contacts_title),
                        fontWeight = FontWeight.SemiBold
                    )
                },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.contacts_search))
                    }
                    IconButton(onClick = onFriendRequestsClick) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = stringResource(R.string.contacts_friend_requests))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        when {
            uiState.isLoading && uiState.contacts.isEmpty() && uiState.groups.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null -> {
                ContactsEmptyState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    title = stringResource(R.string.contacts_load_failed),
                    message = uiState.error ?: stringResource(R.string.error_unknown),
                    actionLabel = stringResource(R.string.friend_requests_retry),
                    onAction = { viewModel.refresh() }
                )
            }
            else -> {
                ContactDirectory(
                    modifier = Modifier.padding(padding),
                    contacts = uiState.contacts,
                    groups = uiState.groups,
                    onFriendRequestsClick = onFriendRequestsClick,
                    onGroupChatsClick = onGroupChatsClick,
                    onSearchClick = onSearchClick,
                    onRefresh = { viewModel.refresh() }
                )
            }
        }
    }
}

@Composable
private fun ContactDirectory(
    modifier: Modifier,
    contacts: List<ContactData>,
    groups: List<GroupData>,
    onFriendRequestsClick: () -> Unit,
    onGroupChatsClick: () -> Unit,
    onSearchClick: () -> Unit,
    onRefresh: () -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize()
    ) {
        item {
            SearchEntry(onClick = onSearchClick)
        }

        item {
            DirectoryActionRow(
                title = stringResource(R.string.contacts_new_friends),
                subtitle = stringResource(R.string.contacts_new_friends_desc),
                color = MaterialTheme.colorScheme.primary,
                icon = {
                    Icon(Icons.Filled.PersonAdd, contentDescription = null, tint = Color.White)
                },
                onClick = onFriendRequestsClick
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 76.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }

        item {
            DirectoryActionRow(
                title = stringResource(R.string.contacts_group_chats),
                subtitle = stringResource(R.string.contacts_group_count, groups.size),
                color = Color(0xFF06B6D4),
                icon = {
                    Icon(Icons.Filled.Group, contentDescription = null, tint = Color.White)
                },
                onClick = onGroupChatsClick
            )
        }

        if (contacts.isNotEmpty()) {
            item {
                SectionHeader(
                    text = stringResource(R.string.contacts_section_contacts),
                    count = contacts.size
                )
            }
            items(
                items = contacts.sortedBy { it.displayName().lowercase() },
                key = { "contact-${it.userId}" }
            ) { contact ->
                ContactRow(contact = contact)
                HorizontalDivider(
                    modifier = Modifier.padding(start = 76.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
        } else {
            item {
                ContactsEmptyState(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    title = stringResource(R.string.contacts_empty),
                    message = stringResource(R.string.contacts_empty_desc),
                    actionLabel = stringResource(R.string.friend_requests_retry),
                    onAction = onRefresh
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
                text = stringResource(R.string.contacts_search_placeholder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DirectoryActionRow(
    title: String,
    subtitle: String,
    color: Color,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(color, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionHeader(text: String, count: Int) {
    Text(
        text = "$text · $count",
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun ContactRow(contact: ContactData) {
    val title = contact.displayName()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NexusAvatar(
            id = contact.userId,
            name = title,
            avatarUrl = contact.avatarUrl,
            size = 48.dp
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "@${contact.username}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ContactsEmptyState(
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
                    Icons.Filled.Group,
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

private fun ContactData.displayName(): String {
    return alias?.takeIf { it.isNotBlank() }
        ?: nickname.takeIf { it.isNotBlank() }
        ?: username.takeIf { it.isNotBlank() }
        ?: "User #$userId"
}
