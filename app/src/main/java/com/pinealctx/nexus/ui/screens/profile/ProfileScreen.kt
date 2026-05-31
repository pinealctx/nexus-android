package com.pinealctx.nexus.ui.screens.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onSettingsClick: () -> Unit = {}) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Me") })

        // Profile header
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
                    modifier = Modifier.size(64.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Nexus User", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "@username",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Settings items
        ListItem(
            headlineContent = { Text("Account") },
            leadingContent = { Icon(Icons.Filled.AccountCircle, contentDescription = null) },
            modifier = Modifier.clickable { onSettingsClick() }
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("Notifications") },
            leadingContent = { Icon(Icons.Filled.Notifications, contentDescription = null) },
            modifier = Modifier.clickable { onSettingsClick() }
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("Privacy") },
            leadingContent = { Icon(Icons.Filled.Lock, contentDescription = null) },
            modifier = Modifier.clickable { onSettingsClick() }
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("About") },
            leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) },
            modifier = Modifier.clickable { onSettingsClick() }
        )
        HorizontalDivider()

        Spacer(modifier = Modifier.weight(1f))

        // Settings button
        Button(
            onClick = onSettingsClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Icon(Icons.Filled.Settings, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Settings")
        }
    }
}
