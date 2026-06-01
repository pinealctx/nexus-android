package com.pinealctx.nexus.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pinealctx.nexus.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToBlockedUsers: () -> Unit = {},
    onNavigateToLanguage: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    onNavigateToDevices: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(stringResource(R.string.settings_logout_title)) },
            text = { Text(stringResource(R.string.settings_logout_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    viewModel.logout()
                    onLogout()
                }) {
                    Text(stringResource(R.string.settings_logout), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
            // Profile section
            if (uiState.profile != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(56.dp),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = (uiState.profile?.nickname?.firstOrNull()
                                        ?: uiState.profile?.username?.firstOrNull()
                                        ?: '?').toString(),
                                    style = MaterialTheme.typography.headlineSmall
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = uiState.profile?.nickname ?: "",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "@${uiState.profile?.username ?: ""}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!uiState.profile?.signature.isNullOrBlank()) {
                                Text(
                                    text = uiState.profile?.signature ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Settings sections
            HorizontalDivider()

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_account)) },
                supportingContent = { Text(stringResource(R.string.settings_account_desc)) },
                leadingContent = {
                    Icon(Icons.Filled.AccountCircle, contentDescription = null)
                },
                modifier = Modifier.clickable { onNavigateToDevices() }
            )
            HorizontalDivider()

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_notifications)) },
                supportingContent = { Text(stringResource(R.string.settings_notifications_desc)) },
                leadingContent = {
                    Icon(Icons.Filled.Notifications, contentDescription = null)
                },
                modifier = Modifier.clickable { onNavigateToNotifications() }
            )
            HorizontalDivider()

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_privacy)) },
                supportingContent = { Text(stringResource(R.string.settings_privacy_desc)) },
                leadingContent = {
                    Icon(Icons.Filled.Lock, contentDescription = null)
                },
                modifier = Modifier.clickable { onNavigateToBlockedUsers() }
            )
            HorizontalDivider()

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_language)) },
                supportingContent = { Text(stringResource(R.string.settings_language_desc)) },
                leadingContent = {
                    Icon(Icons.Filled.Language, contentDescription = null)
                },
                modifier = Modifier.clickable { onNavigateToLanguage() }
            )
            HorizontalDivider()

            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_about)) },
                supportingContent = { Text(stringResource(R.string.settings_about_desc, "0.1.0")) },
                leadingContent = {
                    Icon(Icons.Filled.Info, contentDescription = null)
                },
                modifier = Modifier.clickable { onNavigateToAbout() }
            )
            HorizontalDivider()

            Spacer(modifier = Modifier.weight(1f))

            // Logout button
            Button(
                onClick = { showLogoutDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.settings_logout))
            }
        }
    }
}
