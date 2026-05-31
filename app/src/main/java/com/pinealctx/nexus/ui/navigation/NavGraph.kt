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
    const val GROUP_DETAIL = "group_detail/{groupId}"
    const val CREATE_GROUP = "create_group"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val EDIT_PROFILE = "edit_profile"
    const val DEVICES = "devices"

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
                onGroupClick = { groupId ->
                    navController.navigate(Routes.groupDetailRoute(groupId))
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
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
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
