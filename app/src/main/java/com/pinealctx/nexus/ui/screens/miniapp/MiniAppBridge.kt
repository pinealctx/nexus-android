package com.pinealctx.nexus.ui.screens.miniapp

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONObject

class MiniAppBridge(
    private val context: Context,
    private val permissions: Int,
    private val onClose: () -> Unit,
    private val onReady: () -> Unit,
    private val onMainButtonSetup: (MainButtonState) -> Unit,
    private val onBackButtonSetup: (BackButtonState) -> Unit,
    private val onShare: (String?, String?, String?) -> Unit,
    private val getThemeJson: () -> String,
    private val getUserInfoJson: () -> String,
    private val onScanQr: (() -> Unit)? = null
) {
    private var webView: WebView? = null

    fun attachWebView(webView: WebView) {
        this.webView = webView
    }

    fun dispatchEvent(eventType: String, eventData: String? = null) {
        val js = if (eventData != null) {
            "window.NexusBridge && window.NexusBridge._dispatchEvent('$eventType', $eventData)"
        } else {
            "window.NexusBridge && window.NexusBridge._dispatchEvent('$eventType', null)"
        }
        webView?.evaluateJavascript(js, null)
    }

    @JavascriptInterface
    fun postEvent(eventType: String, eventData: String?) {
        when (eventType) {
            "nexus_app_ready" -> onReady()
            "nexus_app_close" -> onClose()
            "nexus_app_expand" -> {} // No-op on Android (already fullscreen)
            "nexus_app_request_theme" -> handleRequestTheme()
            "nexus_app_request_user_info" -> handleRequestUserInfo()
            "nexus_app_setup_main_button" -> handleSetupMainButton(eventData)
            "nexus_app_setup_back_button" -> handleSetupBackButton(eventData)
            "nexus_app_share" -> handleShare(eventData)
            "nexus_app_open_link" -> handleOpenLink(eventData)
            "nexus_app_haptic_feedback" -> handleHapticFeedback(eventData)
            "nexus_app_read_clipboard" -> handleReadClipboard()
            "nexus_app_scan_qr" -> handleScanQr()
        }
    }

    private fun handleRequestTheme() {
        val themeJson = getThemeJson()
        dispatchEvent("theme_data", themeJson)
    }

    private fun handleRequestUserInfo() {
        val userJson = getUserInfoJson()
        dispatchEvent("user_info_result", userJson)
    }

    private fun handleSetupMainButton(eventData: String?) {
        val data = eventData?.let { JSONObject(it) } ?: return
        val state = MainButtonState(
            text = data.optString("text", ""),
            color = if (data.has("color") && !data.isNull("color")) data.getString("color") else null,
            isVisible = data.optBoolean("isVisible", false),
            isActive = data.optBoolean("isActive", true)
        )
        onMainButtonSetup(state)
        dispatchEvent("main_button_updated", eventData)
    }

    private fun handleSetupBackButton(eventData: String?) {
        val data = eventData?.let { JSONObject(it) } ?: return
        val state = BackButtonState(
            isVisible = data.optBoolean("isVisible", false),
            isActive = data.optBoolean("isActive", true)
        )
        onBackButtonSetup(state)
        dispatchEvent("back_button_updated", eventData)
    }

    private fun handleShare(eventData: String?) {
        val data = eventData?.let { JSONObject(it) } ?: return
        onShare(
            if (data.has("title")) data.optString("title") else null,
            if (data.has("description")) data.optString("description") else null,
            if (data.has("start_param")) data.optString("start_param") else null
        )
    }

    private fun handleOpenLink(eventData: String?) {
        val data = eventData?.let { JSONObject(it) } ?: return
        val url = data.optString("url", "")
        if (url.isNotEmpty()) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    private fun handleHapticFeedback(eventData: String?) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun handleReadClipboard() {
        if (permissions and 1 == 0) {
            dispatchEvent("clipboard_result", """{"error":"permission_not_declared"}""")
            return
        }
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
            val result = JSONObject().put("text", text).toString()
            dispatchEvent("clipboard_result", result)
        } catch (e: Exception) {
            dispatchEvent("clipboard_result", """{"error":"clipboard_read_failed"}""")
        }
    }

    private fun handleScanQr() {
        if (permissions and 2 == 0) {
            dispatchEvent("qr_result", """{"error":"permission_not_declared"}""")
            return
        }
        if (onScanQr != null) {
            onScanQr.invoke()
        } else {
            dispatchEvent("qr_result", """{"error":"not_supported"}""")
        }
    }

    fun deliverQrResult(data: String?) {
        if (data != null) {
            val result = JSONObject().put("data", data).toString()
            dispatchEvent("qr_result", result)
        } else {
            dispatchEvent("qr_result", """{"error":"cancelled"}""")
        }
    }

    companion object {
        const val BRIDGE_JS = """
            (function() {
                if (window.NexusBridge) return;
                var callbacks = [];
                window.NexusBridge = Object.freeze({
                    postEvent: function(eventType, eventData) {
                        NexusBridgeNative.postEvent(eventType, eventData ? JSON.stringify(eventData) : null);
                    },
                    onEvent: function(callback) {
                        callbacks.push(callback);
                    },
                    _dispatchEvent: function(eventType, eventData) {
                        callbacks.forEach(function(cb) {
                            try { cb(eventType, eventData); } catch(e) {}
                        });
                    }
                });
            })();
        """
    }
}

data class MainButtonState(
    val text: String = "",
    val color: String? = null,
    val isVisible: Boolean = false,
    val isActive: Boolean = true
)

data class BackButtonState(
    val isVisible: Boolean = false,
    val isActive: Boolean = true
)
