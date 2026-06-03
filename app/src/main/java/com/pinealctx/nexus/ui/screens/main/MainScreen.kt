package com.pinealctx.nexus.ui.screens.main

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pinealctx.nexus.R
import com.pinealctx.nexus.ui.screens.contacts.ContactsScreen
import com.pinealctx.nexus.ui.screens.conversations.ConversationListScreen
import com.pinealctx.nexus.ui.screens.profile.ProfileScreen
import uniffi.nexus_ffi.ConnectionStatus

enum class MainTab {
    CHATS,
    CONTACTS,
    DISCOVER,
    ME
}

@Composable
fun MainScreen(
    onConversationClick: (String) -> Unit,
    onFriendRequestsClick: () -> Unit = {},
    onGroupClick: (Int) -> Unit = {},
    onSearchClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onAgentMiniApp: ((Int) -> Unit)? = null,
    connectionViewModel: ConnectionViewModel = hiltViewModel()
) {
    var selectedTab by remember { mutableStateOf(MainTab.CHATS) }
    val connectionStatus by connectionViewModel.connectionStatus.collectAsState()

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == MainTab.CHATS,
                    onClick = { selectedTab = MainTab.CHATS },
                    icon = {
                        Icon(
                            if (selectedTab == MainTab.CHATS) Icons.AutoMirrored.Filled.Chat else Icons.AutoMirrored.Outlined.Chat,
                            contentDescription = null
                        )
                    },
                    label = { Text(stringResource(R.string.tab_chats)) },
                    colors = nexusNavigationBarItemColors()
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.CONTACTS,
                    onClick = { selectedTab = MainTab.CONTACTS },
                    icon = {
                        Icon(
                            if (selectedTab == MainTab.CONTACTS) Icons.Filled.People else Icons.Outlined.People,
                            contentDescription = null
                        )
                    },
                    label = { Text(stringResource(R.string.tab_contacts)) },
                    colors = nexusNavigationBarItemColors()
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.DISCOVER,
                    onClick = { selectedTab = MainTab.DISCOVER },
                    icon = {
                        Icon(
                            if (selectedTab == MainTab.DISCOVER) Icons.Filled.Explore else Icons.Outlined.Explore,
                            contentDescription = null
                        )
                    },
                    label = { Text(stringResource(R.string.tab_discover)) },
                    colors = nexusNavigationBarItemColors()
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.ME,
                    onClick = { selectedTab = MainTab.ME },
                    icon = {
                        Icon(
                            if (selectedTab == MainTab.ME) Icons.Filled.Person else Icons.Outlined.Person,
                            contentDescription = null
                        )
                    },
                    label = { Text(stringResource(R.string.tab_me)) },
                    colors = nexusNavigationBarItemColors()
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            ConnectionStatusBar(status = connectionStatus)
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    MainTab.CHATS -> ConversationListScreen(
                        onConversationClick = onConversationClick,
                        onSearchClick = onSearchClick
                    )
                    MainTab.CONTACTS -> ContactsScreen(
                        onFriendRequestsClick = onFriendRequestsClick,
                        onSearchClick = onSearchClick,
                        onGroupClick = onGroupClick
                    )
                    MainTab.DISCOVER -> com.pinealctx.nexus.ui.screens.agents.AgentsScreen(
                        onOpenMiniApp = { agentUserId -> onAgentMiniApp?.invoke(agentUserId) }
                    )
                    MainTab.ME -> ProfileScreen(onSettingsClick = onSettingsClick)
                }
            }
        }
    }
}

@Composable
private fun nexusNavigationBarItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.primary,
    selectedTextColor = MaterialTheme.colorScheme.primary,
    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
)

@Composable
fun ConnectionStatusBar(status: ConnectionStatus) {
    when (status) {
        ConnectionStatus.CONNECTING,
        ConnectionStatus.RECONNECTING -> {
            Surface(
                color = Color(0xFFFFF3D6),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFFB45309)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (status == ConnectionStatus.CONNECTING) {
                            stringResource(R.string.status_connecting)
                        } else {
                            stringResource(R.string.status_reconnecting)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF92400E)
                    )
                }
            }
        }
        ConnectionStatus.DISCONNECTED -> {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.status_disconnected),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        ConnectionStatus.CONNECTED -> {}
    }
}
