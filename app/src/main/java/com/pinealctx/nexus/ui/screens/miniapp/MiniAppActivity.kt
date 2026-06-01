package com.pinealctx.nexus.ui.screens.miniapp

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.pinealctx.nexus.R
import com.pinealctx.nexus.core.managers.AgentManager
import com.pinealctx.nexus.core.managers.UserManager
import com.pinealctx.nexus.ui.theme.NexusTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URLEncoder
import javax.inject.Inject

@AndroidEntryPoint
class MiniAppActivity : ComponentActivity() {

    @Inject lateinit var agentManager: AgentManager
    @Inject lateinit var userManager: UserManager

    private var qrResultCallback: ((String?) -> Unit)? = null

    private val qrScanLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = if (result.resultCode == RESULT_OK) {
            result.data?.getStringExtra(QrScannerActivity.RESULT_QR_DATA)
        } else null
        qrResultCallback?.invoke(data)
        qrResultCallback = null
    }

    companion object {
        const val EXTRA_AGENT_USER_ID = "agent_user_id"
        const val EXTRA_CONVERSATION_ID = "conversation_id"
        const val EXTRA_START_PARAM = "start_param"
        const val EXTRA_AGENT_NAME = "agent_name"
        const val EXTRA_PERMISSIONS = "permissions"
        private const val READY_TIMEOUT_MS = 10_000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val agentUserId = intent.getIntExtra(EXTRA_AGENT_USER_ID, 0)
        val conversationId = intent.getLongExtra(EXTRA_CONVERSATION_ID, 0L)
        val startParam = intent.getStringExtra(EXTRA_START_PARAM) ?: ""
        val agentName = intent.getStringExtra(EXTRA_AGENT_NAME) ?: "Mini App"
        val permissions = intent.getIntExtra(EXTRA_PERMISSIONS, 0)

        if (agentUserId == 0) {
            finish()
            return
        }

        setContent {
            NexusTheme {
                MiniAppScreen(
                    agentUserId = agentUserId,
                    conversationId = conversationId,
                    startParam = startParam,
                    agentName = agentName,
                    permissions = permissions,
                    agentManager = agentManager,
                    userManager = userManager,
                    onClose = { finish() },
                    onScanQr = { callback ->
                        qrResultCallback = callback
                        qrScanLauncher.launch(Intent(this, QrScannerActivity::class.java))
                    }
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MiniAppScreen(
    agentUserId: Int,
    conversationId: Long,
    startParam: String,
    agentName: String,
    permissions: Int,
    agentManager: AgentManager,
    userManager: UserManager,
    onClose: () -> Unit,
    onScanQr: ((callback: (String?) -> Unit) -> Unit)? = null
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val isDarkTheme = isSystemInDarkTheme()
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(true) }
    var isReady by remember { mutableStateOf(false) }
    var isTimeout by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var miniAppUrl by remember { mutableStateOf<String?>(null) }
    var mainButton by remember { mutableStateOf(MainButtonState()) }
    var backButton by remember { mutableStateOf(BackButtonState()) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    val bridge = remember {
        MiniAppBridge(
            context = context,
            permissions = permissions,
            onClose = onClose,
            onReady = {
                isReady = true
                isLoading = false
            },
            onMainButtonSetup = { mainButton = it },
            onBackButtonSetup = { backButton = it },
            onShare = { title, _, _ ->
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, title ?: agentName)
                    putExtra(Intent.EXTRA_TEXT, "Check out this Mini App!")
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share"))
            },
            getThemeJson = { MiniAppTheme.getThemeParams(colorScheme) },
            getUserInfoJson = {
                try {
                    val profile = userManager.getMyProfile()
                    if (profile != null) {
                        JSONObject().apply {
                            put("user_id", profile.userId)
                            put("username", profile.username)
                            put("nickname", profile.nickname)
                            put("avatar_url", profile.avatarUrl)
                        }.toString()
                    } else {
                        """{"user_id":0,"username":"","nickname":"","avatar_url":""}"""
                    }
                } catch (_: Exception) {
                    """{"user_id":0,"username":"","nickname":"","avatar_url":""}"""
                }
            },
            onScanQr = if (onScanQr != null) {
                { onScanQr { result -> bridge.deliverQrResult(result) } }
            } else null
        )
    }

    // Theme sync: push updated theme when system theme changes
    LaunchedEffect(isDarkTheme) {
        if (isReady) {
            val themeJson = MiniAppTheme.getThemeParams(colorScheme)
            bridge.dispatchEvent("theme_changed", themeJson)
        }
    }

    // Fetch launch data
    LaunchedEffect(agentUserId) {
        scope.launch(Dispatchers.IO) {
            try {
                val launchData = agentManager.getMiniAppLaunchData(
                    agentUserId, conversationId, startParam
                )
                val themeParams = MiniAppTheme.getThemeParams(colorScheme)
                val encodedData = URLEncoder.encode(launchData.initData, "UTF-8")
                val encodedTheme = URLEncoder.encode(themeParams, "UTF-8")
                val url = "${launchData.miniAppUrl}#nexusAppData=$encodedData&nexusAppVersion=1.0&nexusAppPlatform=android&nexusAppThemeParams=$encodedTheme"
                miniAppUrl = url
            } catch (e: Exception) {
                error = e.message ?: "Failed to load Mini App"
                isLoading = false
            }
        }
    }

    // Ready timeout
    LaunchedEffect(miniAppUrl) {
        if (miniAppUrl != null) {
            Handler(Looper.getMainLooper()).postDelayed({
                if (!isReady) {
                    isTimeout = true
                    bridge.dispatchEvent("load_timeout")
                }
            }, 10_000L)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(agentName, maxLines = 1) },
                navigationIcon = {
                    if (backButton.isVisible) {
                        IconButton(onClick = {
                            bridge.dispatchEvent("back_button_pressed")
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.miniapp_close))
                    }
                }
            )
        },
        bottomBar = {
            AnimatedVisibility(visible = mainButton.isVisible) {
                Surface(tonalElevation = 2.dp) {
                    val buttonColor = mainButton.color?.let {
                        try { Color(android.graphics.Color.parseColor(it)) }
                        catch (_: Exception) { MaterialTheme.colorScheme.primary }
                    } ?: MaterialTheme.colorScheme.primary

                    Button(
                        onClick = { bridge.dispatchEvent("main_button_pressed") },
                        enabled = mainButton.isActive,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = buttonColor)
                    ) {
                        Text(mainButton.text)
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            miniAppUrl?.let { url ->
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            settings.allowFileAccess = false

                            addJavascriptInterface(bridge, "NexusBridgeNative")
                            bridge.attachWebView(this)

                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    view?.evaluateJavascript(MiniAppBridge.BRIDGE_JS, null)
                                }

                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    val reqUrl = request?.url?.toString() ?: return false
                                    if (!reqUrl.startsWith("http")) {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(reqUrl)))
                                        return true
                                    }
                                    // Origin validation: only allow same-origin navigation
                                    val allowedHost = miniAppUrl?.let { Uri.parse(it.substringBefore("#")) }?.host
                                    val requestHost = Uri.parse(reqUrl).host
                                    if (allowedHost != null && requestHost != null && requestHost != allowedHost) {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(reqUrl)))
                                        return true
                                    }
                                    return false
                                }
                            }

                            webChromeClient = WebChromeClient()
                            loadUrl(url)
                            webViewRef = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            error?.let { msg ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(msg, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(onClick = onClose) { Text(stringResource(R.string.miniapp_close)) }
                    }
                }
            }

            if (isTimeout && !isReady) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Text(
                        text = "Mini App is taking longer than expected...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
