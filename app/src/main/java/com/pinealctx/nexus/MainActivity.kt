package com.pinealctx.nexus

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.pinealctx.nexus.core.SyncManager
import com.pinealctx.nexus.core.managers.AgentManager
import com.pinealctx.nexus.core.managers.UserManager
import com.pinealctx.nexus.ui.navigation.NexusNavGraph
import com.pinealctx.nexus.ui.navigation.Routes
import com.pinealctx.nexus.ui.screens.miniapp.MiniAppActivity
import com.pinealctx.nexus.ui.theme.NexusTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var syncManager: SyncManager
    @Inject lateinit var agentManager: AgentManager
    @Inject lateinit var userManager: UserManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleDeepLink(intent)
        setContent {
            NexusTheme {
                val navController = rememberNavController()

                LaunchedEffect(Unit) {
                    syncManager.onForceLogout = {
                        runOnUiThread {
                            navController.navigate(Routes.LOGIN) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }
                }

                NexusNavGraph(navController = navController)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
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
                            Toast.makeText(this@MainActivity, "Agent not found", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (_: Exception) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Failed to open Mini App", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        syncManager.onForceLogout = null
    }
}
