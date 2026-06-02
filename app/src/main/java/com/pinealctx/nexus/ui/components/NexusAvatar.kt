package com.pinealctx.nexus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

enum class NexusAvatarBadge {
    Agent,
    SystemAgent,
    Group
}

private val AvatarColors = listOf(
    Color(0xFF4F46E5),
    Color(0xFF06B6D4),
    Color(0xFFF97316),
    Color(0xFFA855F7),
    Color(0xFFEC4899),
    Color(0xFF22C55E),
    Color(0xFFEF4444),
    Color(0xFF0EA5E9)
)

@Composable
fun NexusAvatar(
    id: Int,
    name: String?,
    avatarUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    badge: NexusAvatarBadge? = null
) {
    val radius = when {
        size <= 28.dp -> 8.dp
        size <= 48.dp -> 12.dp
        else -> 16.dp
    }
    val shape = RoundedCornerShape(radius)
    val fallbackColor = AvatarColors[kotlin.math.abs(id).rem(AvatarColors.size)]
    val initial = name?.trim()?.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(shape)
                .background(fallbackColor),
            contentAlignment = Alignment.Center
        ) {
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(size)
                        .clip(shape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        if (badge != null) {
            AvatarBadge(
                badge = badge,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }
}

@Composable
private fun AvatarBadge(
    badge: NexusAvatarBadge,
    modifier: Modifier = Modifier
) {
    val background = when (badge) {
        NexusAvatarBadge.Agent -> MaterialTheme.colorScheme.primary
        NexusAvatarBadge.SystemAgent -> Color(0xFF2563EB)
        NexusAvatarBadge.Group -> MaterialTheme.colorScheme.primary
    }
    val icon = when (badge) {
        NexusAvatarBadge.Agent -> Icons.Filled.SmartToy
        NexusAvatarBadge.SystemAgent -> Icons.Filled.VerifiedUser
        NexusAvatarBadge.Group -> Icons.Filled.Groups
    }

    Box(
        modifier = modifier
            .size(17.dp)
            .clip(RoundedCornerShape(50))
            .background(background)
            .border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(50)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(10.dp),
            tint = Color.White
        )
    }
}
