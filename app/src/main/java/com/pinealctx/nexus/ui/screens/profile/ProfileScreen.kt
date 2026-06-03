package com.pinealctx.nexus.ui.screens.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pinealctx.nexus.R
import com.pinealctx.nexus.core.ProfileData
import com.pinealctx.nexus.ui.components.NexusAvatar
import com.pinealctx.nexus.ui.components.NexusMainHeader

@Composable
fun ProfileScreen(
    onEditProfileClick: () -> Unit = {},
    onDevicesClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    onPrivacyClick: () -> Unit = {},
    onLanguageClick: () -> Unit = {},
    onAboutClick: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        NexusMainHeader(title = stringResource(R.string.tab_me))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                ProfileSummary(
                    uiState = uiState,
                    onClick = onEditProfileClick,
                    onRetry = { viewModel.loadProfile() }
                )
            }

            item {
                ProfileMenuItem(
                    title = stringResource(R.string.settings_account),
                    subtitle = stringResource(R.string.settings_account_desc),
                    icon = Icons.Filled.AccountCircle,
                    onClick = onEditProfileClick
                )
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
            }
            item {
                ProfileMenuItem(
                    title = stringResource(R.string.devices_title),
                    subtitle = stringResource(R.string.settings_devices_desc),
                    icon = Icons.Filled.Devices,
                    onClick = onDevicesClick
                )
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
            }
            item {
                ProfileMenuItem(
                    title = stringResource(R.string.settings_notifications),
                    subtitle = stringResource(R.string.settings_notifications_desc),
                    icon = Icons.Filled.Notifications,
                    onClick = onNotificationsClick
                )
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
            }
            item {
                ProfileMenuItem(
                    title = stringResource(R.string.settings_privacy),
                    subtitle = stringResource(R.string.settings_privacy_desc),
                    icon = Icons.Filled.Lock,
                    onClick = onPrivacyClick
                )
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
            }
            item {
                ProfileMenuItem(
                    title = stringResource(R.string.settings_language),
                    subtitle = stringResource(R.string.settings_language_desc),
                    icon = Icons.Filled.Language,
                    onClick = onLanguageClick
                )
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
            }
            item {
                ProfileMenuItem(
                    title = stringResource(R.string.settings_about),
                    subtitle = stringResource(R.string.settings_about_desc, "0.1.0"),
                    icon = Icons.Filled.Info,
                    onClick = onAboutClick
                )
            }
        }
    }
}

@Composable
private fun ProfileSummary(
    uiState: ProfileUiState,
    onClick: () -> Unit,
    onRetry: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(enabled = !uiState.isLoading && uiState.error == null, onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        when {
            uiState.isLoading -> LoadingProfileSummary()
            uiState.error != null -> ProfileErrorSummary(
                error = uiState.error ?: stringResource(R.string.error_unknown),
                onRetry = onRetry
            )
            else -> LoadedProfileSummary(profile = uiState.profile)
        }
    }
}

@Composable
private fun LoadingProfileSummary() {
    Row(
        modifier = Modifier.padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
        Spacer(modifier = Modifier.width(16.dp))
        Text(stringResource(R.string.loading))
    }
}

@Composable
private fun ProfileErrorSummary(error: String, onRetry: () -> Unit) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(
            stringResource(R.string.profile_load_failed),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(onClick = onRetry, shape = RoundedCornerShape(8.dp)) {
            Text(stringResource(R.string.friend_requests_retry))
        }
    }
}

@Composable
private fun LoadedProfileSummary(profile: ProfileData?) {
    val displayName = profile?.displayName() ?: stringResource(R.string.profile_unknown_user)
    Row(
        modifier = Modifier.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NexusAvatar(
            id = profile?.userId ?: 0,
            name = displayName,
            avatarUrl = profile?.avatarUrl ?: "",
            size = 64.dp
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                displayName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                profile?.username?.takeIf { it.isNotBlank() }?.let { "@$it" }
                    ?: stringResource(R.string.profile_username_missing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            profile?.signature?.takeIf { it.isNotBlank() }?.let { signature ->
                Text(
                    signature,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ProfileMenuItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        supportingContent = {
            Text(
                subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

private fun ProfileData.displayName(): String {
    return nickname.takeIf { it.isNotBlank() }
        ?: username.takeIf { it.isNotBlank() }
        ?: "User #$userId"
}
