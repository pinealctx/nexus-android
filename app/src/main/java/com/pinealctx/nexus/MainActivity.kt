package com.pinealctx.nexus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.rememberNavController
import com.pinealctx.nexus.core.SyncManager
import com.pinealctx.nexus.ui.navigation.NexusNavGraph
import com.pinealctx.nexus.ui.navigation.Routes
import com.pinealctx.nexus.ui.theme.NexusTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var syncManager: SyncManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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

    override fun onDestroy() {
        super.onDestroy()
        syncManager.onForceLogout = null
    }
}
