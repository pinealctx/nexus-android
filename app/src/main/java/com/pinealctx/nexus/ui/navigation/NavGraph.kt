package com.pinealctx.nexus.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.pinealctx.nexus.ui.screens.chat.ChatScreen
import com.pinealctx.nexus.ui.screens.friends.FriendRequestsScreen
import com.pinealctx.nexus.ui.screens.groups.GroupChatsScreen
import com.pinealctx.nexus.ui.screens.groups.GroupDetailScreen
import com.pinealctx.nexus.ui.screens.login.LoginScreen
import com.pinealctx.nexus.ui.screens.main.MainScreen
import com.pinealctx.nexus.ui.screens.search.SearchScreen
import com.pinealctx.nexus.ui.screens.settings.SettingsScreen

object Routes {
    const val LOGIN = "login"
    const val MAIN = "main"
    const val CHAT = "chat/{conversationId}"
    const val FRIEND_REQUESTS = "friend_requests"
    const val GROUP_CHATS = "group_chats"
    const val GROUP_DETAIL = "group_detail/{groupId}"
    const val CREATE_GROUP = "create_group"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val EDIT_PROFILE = "edit_profile"
    const val DEVICES = "devices"
    const val NOTIFICATION_SETTINGS = "settings/notifications"
    const val BLOCKED_USERS = "settings/blocked"
    const val LANGUAGE_SETTINGS = "settings/language"
    const val ABOUT = "settings/about"

    fun chatRoute(conversationId: String) = "chat/$conversationId"
    fun groupDetailRoute(groupId: Int) = "group_detail/$groupId"
}

@Composable
fun NexusNavGraph(navController: NavHostController) {
    val startDestination = Routes.LOGIN

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.MAIN) {
            val context = LocalContext.current
            MainScreen(
                onConversationClick = { conversationId ->
                    navController.navigate(Routes.chatRoute(conversationId))
                },
                onFriendRequestsClick = {
                    navController.navigate(Routes.FRIEND_REQUESTS)
                },
                onGroupChatsClick = {
                    navController.navigate(Routes.GROUP_CHATS)
                },
                onSearchClick = {
                    navController.navigate(Routes.SEARCH)
                },
                onSettingsClick = {
                    navController.navigate(Routes.SETTINGS)
                },
                onAgentMiniApp = { agentUserId ->
                    val intent = Intent(context, com.pinealctx.nexus.ui.screens.miniapp.MiniAppActivity::class.java).apply {
                        putExtra(com.pinealctx.nexus.ui.screens.miniapp.MiniAppActivity.EXTRA_AGENT_USER_ID, agentUserId)
                        putExtra(com.pinealctx.nexus.ui.screens.miniapp.MiniAppActivity.EXTRA_CONVERSATION_ID, 0L)
                        putExtra(com.pinealctx.nexus.ui.screens.miniapp.MiniAppActivity.EXTRA_START_PARAM, "")
                    }
                    context.startActivity(intent)
                }
            )
        }

        composable(Routes.CHAT) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: ""
            ChatScreen(
                conversationId = conversationId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.FRIEND_REQUESTS) {
            FriendRequestsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.GROUP_CHATS) {
            GroupChatsScreen(
                onBack = { navController.popBackStack() },
                onGroupClick = { groupId ->
                    navController.navigate(Routes.groupDetailRoute(groupId))
                },
                onCreateGroupClick = {
                    navController.navigate(Routes.CREATE_GROUP)
                }
            )
        }

        composable(
            route = Routes.GROUP_DETAIL,
            arguments = listOf(navArgument("groupId") { type = NavType.IntType })
        ) {
            GroupDetailScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SEARCH) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onNavigateToChat = { conversationId, _ ->
                    navController.navigate("chat/$conversationId")
                }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToNotifications = {
                    navController.navigate(Routes.NOTIFICATION_SETTINGS)
                },
                onNavigateToBlockedUsers = {
                    navController.navigate(Routes.BLOCKED_USERS)
                },
                onNavigateToLanguage = {
                    navController.navigate(Routes.LANGUAGE_SETTINGS)
                },
                onNavigateToAbout = {
                    navController.navigate(Routes.ABOUT)
                },
                onNavigateToDevices = {
                    navController.navigate(Routes.DEVICES)
                }
            )
        }

        composable(Routes.NOTIFICATION_SETTINGS) {
            com.pinealctx.nexus.ui.screens.settings.NotificationSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.BLOCKED_USERS) {
            com.pinealctx.nexus.ui.screens.settings.BlockedUsersScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.LANGUAGE_SETTINGS) {
            com.pinealctx.nexus.ui.screens.settings.LanguageSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.ABOUT) {
            com.pinealctx.nexus.ui.screens.settings.AboutScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.CREATE_GROUP) {
            com.pinealctx.nexus.ui.screens.groups.CreateGroupScreen(
                onBack = { navController.popBackStack() },
                onGroupCreated = { groupId ->
                    navController.popBackStack()
                    navController.navigate(Routes.groupDetailRoute(groupId))
                }
            )
        }

        composable(Routes.EDIT_PROFILE) {
            com.pinealctx.nexus.ui.screens.profile.EditProfileScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.DEVICES) {
            com.pinealctx.nexus.ui.screens.settings.DevicesScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
