package com.pinealctx.nexus.ui.screens.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pinealctx.nexus.R
import com.pinealctx.nexus.ui.components.NexusMainHeader

@Composable
fun ProfileScreen(
    onSettingsClick: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        NexusMainHeader(title = stringResource(R.string.tab_me))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            when {
                uiState.isLoading -> {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(stringResource(R.string.loading))
                    }
                }
                uiState.error != null -> {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.profile_load_failed),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            uiState.error ?: stringResource(R.string.error_unknown),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        FilledTonalButton(onClick = { viewModel.loadProfile() }) {
                            Text(stringResource(R.string.friend_requests_retry))
                        }
                    }
                }
                else -> {
                    val profile = uiState.profile
                    val displayName = profile?.nickname?.takeIf { it.isNotBlank() }
                        ?: profile?.username?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.profile_unknown_user)
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(64.dp),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = displayName.firstOrNull()?.uppercase() ?: "?",
                                    style = MaterialTheme.typography.headlineSmall
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                displayName,
                                style = MaterialTheme.typography.titleLarge,
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
                            if (!profile?.signature.isNullOrBlank()) {
                                Text(
                                    profile?.signature ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_account)) },
            supportingContent = { Text(stringResource(R.string.settings_account_desc)) },
            leadingContent = { Icon(Icons.Filled.AccountCircle, contentDescription = null) },
            modifier = Modifier.clickable { onSettingsClick() }
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_notifications)) },
            supportingContent = { Text(stringResource(R.string.settings_notifications_desc)) },
            leadingContent = { Icon(Icons.Filled.Notifications, contentDescription = null) },
            modifier = Modifier.clickable { onSettingsClick() }
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_privacy)) },
            supportingContent = { Text(stringResource(R.string.settings_privacy_desc)) },
            leadingContent = { Icon(Icons.Filled.Lock, contentDescription = null) },
            modifier = Modifier.clickable { onSettingsClick() }
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_about)) },
            supportingContent = { Text(stringResource(R.string.settings_about_desc, "0.1.0")) },
            leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) },
            modifier = Modifier.clickable { onSettingsClick() }
        )
        HorizontalDivider()

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onSettingsClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Icon(Icons.Filled.Settings, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.settings_title))
        }
    }
}
