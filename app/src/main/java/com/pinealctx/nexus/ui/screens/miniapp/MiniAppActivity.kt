package com.pinealctx.nexus.ui.screens.miniapp

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.pinealctx.nexus.ui.theme.NexusTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MiniAppActivity : ComponentActivity() {

    companion object {
        const val EXTRA_MINI_APP_URL = "mini_app_url"
        const val EXTRA_INIT_DATA = "init_data"
        const val EXTRA_AGENT_NAME = "agent_name"
        const val EXTRA_AGENT_USER_ID = "agent_user_id"
        const val EXTRA_CONVERSATION_ID = "conversation_id"
        const val EXTRA_PERMISSIONS = "permissions"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val miniAppUrl = intent.getStringExtra(EXTRA_MINI_APP_URL) ?: run { finish(); return }
        val initData = intent.getStringExtra(EXTRA_INIT_DATA) ?: ""
        val agentName = intent.getStringExtra(EXTRA_AGENT_NAME) ?: "Mini App"
        val permissions = intent.getIntExtra(EXTRA_PERMISSIONS, 0)

        val fullUrl = buildMiniAppUrl(miniAppUrl, initData)

        setContent {
            NexusTheme {
                MiniAppScreen(
                    title = agentName,
                    url = fullUrl,
                    permissions = permissions,
                    onClose = { finish() }
                )
            }
        }
    }

    private fun buildMiniAppUrl(baseUrl: String, initData: String): String {
        val params = buildString {
            append("nexusAppData=")
            append(Uri.encode(initData))
            append("&nexusAppVersion=1.0")
            append("&nexusAppPlatform=android")
        }
        return if (baseUrl.contains("#")) {
            "$baseUrl&$params"
        } else {
            "$baseUrl#$params"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MiniAppScreen(
    title: String,
    url: String,
    permissions: Int,
    onClose: () -> Unit
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var pageTitle by remember { mutableStateOf(title) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(pageTitle, maxLines = 1) },
                navigationIcon = {
                    if (canGoBack) {
                        IconButton(onClick = { webView?.goBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }
            )
        }
    ) { padding ->
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowContentAccess = true
                    settings.mediaPlaybackRequiresUserGesture = false

                    addJavascriptInterface(
                        NexusBridgeInterface(
                            permissions = permissions,
                            onClose = onClose,
                            onOpenLink = { linkUrl ->
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(linkUrl)))
                            }
                        ),
                        "NexusBridgeNative"
                    )

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            canGoBack = view?.canGoBack() ?: false
                            view?.evaluateJavascript(BRIDGE_INJECTION_JS, null)
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val requestUrl = request?.url?.toString() ?: return false
                            if (requestUrl.startsWith("http://") || requestUrl.startsWith("https://")) {
                                return false
                            }
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(requestUrl)))
                            return true
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onReceivedTitle(view: WebView?, t: String?) {
                            if (!t.isNullOrBlank() && !t.startsWith("http")) {
                                pageTitle = t
                            }
                        }
                    }

                    loadUrl(url)
                    webView = this
                }
            }
        )
    }
}

class NexusBridgeInterface(
    private val permissions: Int,
    private val onClose: () -> Unit,
    private val onOpenLink: (String) -> Unit
) {
    @JavascriptInterface
    fun postEvent(eventType: String, eventData: String?) {
        when (eventType) {
            "nexus_app_ready" -> {}
            "nexus_app_close" -> onClose()
            "nexus_app_open_link" -> {
                val url = eventData ?: return
                onOpenLink(url)
            }
            "nexus_app_haptic_feedback" -> {}
            "nexus_app_read_clipboard" -> {
                if (permissions and 1 == 0) return
            }
        }
    }
}

private const val BRIDGE_INJECTION_JS = """
(function() {
    if (window.NexusBridge) return;
    var callbacks = [];
    window.NexusBridge = {
        postEvent: function(eventType, eventData) {
            NexusBridgeNative.postEvent(eventType, eventData ? JSON.stringify(eventData) : null);
        },
        onEvent: function(callback) {
            callbacks.push(callback);
        },
        _dispatchEvent: function(eventType, eventData) {
            callbacks.forEach(function(cb) { cb(eventType, eventData); });
        }
    };
    window.NexusBridge.postEvent('nexus_app_ready');
})();
"""
