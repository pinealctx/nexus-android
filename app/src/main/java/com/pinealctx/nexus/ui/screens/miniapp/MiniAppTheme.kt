package com.pinealctx.nexus.ui.screens.miniapp

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.json.JSONObject

object MiniAppTheme {

    fun getThemeParams(colorScheme: ColorScheme): String {
        val params = JSONObject().apply {
            put("bg_color", colorScheme.surface.toHex())
            put("text_color", colorScheme.onSurface.toHex())
            put("hint_color", colorScheme.onSurfaceVariant.toHex())
            put("link_color", colorScheme.primary.toHex())
            put("button_color", colorScheme.primary.toHex())
            put("button_text_color", colorScheme.onPrimary.toHex())
            put("secondary_bg_color", colorScheme.surfaceVariant.toHex())
        }
        return params.toString()
    }

    private fun Color.toHex(): String {
        val argb = this.toArgb()
        return String.format("#%06X", 0xFFFFFF and argb)
    }
}
