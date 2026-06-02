package com.pinealctx.nexus.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val NexusPrimary = Color(0xFF3D7CFF)
private val NexusPrimaryDark = Color(0xFF5B93FF)
private val NexusDanger = Color(0xFFEF4444)

private val LightColorScheme = lightColorScheme(
    primary = NexusPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F0FE),
    onPrimaryContainer = Color(0xFF12356F),
    secondary = Color(0xFF64748B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1F5F9),
    onSecondaryContainer = Color(0xFF334155),
    tertiary = Color(0xFF06B6D4),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0F7FA),
    onTertiaryContainer = Color(0xFF164E63),
    background = Color.White,
    onBackground = Color(0xFF0A0A0A),
    surface = Color.White,
    onSurface = Color(0xFF0A0A0A),
    surfaceVariant = Color(0xFFF5F5F5),
    onSurfaceVariant = Color(0xFF737373),
    outline = Color(0xFFE5E5E5),
    outlineVariant = Color(0xFFF1F5F9),
    error = NexusDanger,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B)
)

private val DarkColorScheme = darkColorScheme(
    primary = NexusPrimaryDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1A2744),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = Color(0xFFA3A3A3),
    onSecondary = Color(0xFF0A0A0A),
    secondaryContainer = Color(0xFF262626),
    onSecondaryContainer = Color(0xFFE5E5E5),
    tertiary = Color(0xFF67E0E6),
    onTertiary = Color(0xFF062F36),
    tertiaryContainer = Color(0xFF12373D),
    onTertiaryContainer = Color(0xFFCCFBF1),
    background = Color(0xFF0A0A0A),
    onBackground = Color(0xFFFAFAFA),
    surface = Color(0xFF141414),
    onSurface = Color(0xFFFAFAFA),
    surfaceVariant = Color(0xFF262626),
    onSurfaceVariant = Color(0xFFA3A3A3),
    outline = Color(0xFF262626),
    outlineVariant = Color(0xFF3A3A3A),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF3F1212),
    onErrorContainer = Color(0xFFFECACA)
)

private val NexusShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

@Composable
fun NexusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor -> if (darkTheme) DarkColorScheme else LightColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = NexusShapes,
        content = content
    )
}
