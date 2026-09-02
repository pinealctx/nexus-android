package com.pinealctx.nexus

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.repeatOnLifecycle
import com.pinealctx.nexus.core.AppEventBus
import com.pinealctx.nexus.core.SyncManager
import com.pinealctx.nexus.core.SecureStorage
import com.pinealctx.nexus.core.managers.AgentManager
import com.pinealctx.nexus.core.managers.PushManager
import com.pinealctx.nexus.core.managers.UserManager
import com.pinealctx.nexus.ui.navigation.NexusNavGraph
import com.pinealctx.nexus.ui.navigation.Routes
import com.pinealctx.nexus.ui.screens.miniapp.MiniAppActivity
import com.pinealctx.nexus.ui.theme.NexusTheme
import com.pinealctx.nexus.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var syncManager: SyncManager
    @Inject lateinit var agentManager: AgentManager
    @Inject lateinit var userManager: UserManager
    @Inject lateinit var secureStorage: SecureStorage
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var pushManager: PushManager
    @Inject lateinit var appEventBus: AppEventBus

    private val pendingNotificationRoute = MutableStateFlow<String?>(null)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        handleNotificationIntent(intent)
        handleDeepLink(intent)
        observeCardActionAnswers()
        setContent {
            NexusTheme {
                val navController = rememberNavController()
                val notificationRoute by pendingNotificationRoute.collectAsState()

                LaunchedEffect(Unit) {
                    syncManager.onForceLogout = {
                        runOnUiThread {
                            navController.navigate(Routes.LOGIN) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }
                }

                LaunchedEffect(notificationRoute) {
                    val route = notificationRoute ?: return@LaunchedEffect
                    if (!secureStorage.hasTokens()) return@LaunchedEffect
                    if (navController.currentDestination?.route != Routes.MAIN) {
                        val returnedToMain = navController.popBackStack(Routes.MAIN, inclusive = false)
                        if (!returnedToMain) {
                            navController.navigate(Routes.MAIN) {
                                launchSingleTop = true
                            }
                        }
                    }
                    navController.navigate(route) {
                        launchSingleTop = true
                    }
                    pendingNotificationRoute.value = null
                }

                NexusNavGraph(
                    navController = navController,
                    onAuthenticated = ::requestNotificationPermissionIfNeeded
                )
            }
        }
    }

    private fun observeCardActionAnswers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                appEventBus.cardActionAnswered().collect { answer ->
                    if (answer.showAlert) {
                        AlertDialog.Builder(this@MainActivity)
                            .setMessage(answer.text)
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    } else {
                        Toast.makeText(this@MainActivity, answer.text, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            secureStorage.hasTokens() &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleNotificationIntent(intent: Intent) {
        val conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID)
        pendingNotificationRoute.value = when {
            !conversationId.isNullOrBlank() -> Routes.chatRoute(conversationId)
            intent.getStringExtra(EXTRA_NAVIGATE_TO) == NAVIGATE_FRIEND_REQUESTS ->
                Routes.FRIEND_REQUESTS
            else -> return
        }
        intent.removeExtra(EXTRA_CONVERSATION_ID)
        intent.removeExtra(EXTRA_NAVIGATE_TO)
    }

    private fun handleDeepLink(intent: Intent) {
        val uri = intent.data ?: return
        if (uri.scheme == "nexus" && uri.host == "miniapp") {
            val agentUsername = uri.pathSegments.firstOrNull() ?: return
            val startParam = uri.getQueryParameter("startparam") ?: ""
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val agent = userManager.resolveUsername(agentUsername)
                    if (agent != null) {
                        val agentInfo = agentManager.getAgentInfo(agent.userId)
                        val launchIntent = Intent(this@MainActivity, MiniAppActivity::class.java).apply {
                            putExtra(MiniAppActivity.EXTRA_AGENT_USER_ID, agent.userId)
                            putExtra(MiniAppActivity.EXTRA_CONVERSATION_ID, 0L)
                            putExtra(MiniAppActivity.EXTRA_START_PARAM, startParam)
                            putExtra(MiniAppActivity.EXTRA_AGENT_NAME, agent.nickname)
                            putExtra(MiniAppActivity.EXTRA_PERMISSIONS, agentInfo?.miniAppPermissions ?: 0)
                        }
                        startActivity(launchIntent)
                    } else {
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, R.string.miniapp_agent_not_found, Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (_: Exception) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, R.string.miniapp_open_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        syncManager.onForceLogout = null
    }

    override fun onResume() {
        super.onResume()
        notificationHelper.cancelAll()
        if (secureStorage.hasTokens()) {
            lifecycleScope.launch(Dispatchers.IO) {
                runCatching { pushManager.clearBadge() }
            }
        }
    }

    private companion object {
        const val EXTRA_CONVERSATION_ID = "conversationId"
        const val EXTRA_NAVIGATE_TO = "navigateTo"
        const val NAVIGATE_FRIEND_REQUESTS = "friend_requests"
    }
}
