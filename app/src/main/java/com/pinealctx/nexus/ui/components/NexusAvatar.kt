package com.pinealctx.nexus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MilitaryTech
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
import androidx.compose.ui.graphics.Brush
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

enum class NexusAvatarIndicator {
    Owner
}

object NexusAvatarDefaults {
    val AvatarColors = listOf(
        Color(0xFF4F46E5),
        Color(0xFF06B6D4),
        Color(0xFFF97316),
        Color(0xFFA855F7),
        Color(0xFFEC4899),
        Color(0xFF22C55E),
        Color(0xFFEF4444),
        Color(0xFF0EA5E9)
    )

    fun colorForId(id: Int): Color {
        val index = kotlin.math.abs(id.toLong()).rem(AvatarColors.size).toInt()
        return AvatarColors[index]
    }

    fun radiusForSize(size: Dp): Dp = when {
        size <= 28.dp -> 8.dp
        size <= 48.dp -> 12.dp
        else -> 16.dp
    }

    fun badgeSizeForAvatar(size: Dp): Dp = when {
        size <= 28.dp -> 14.dp
        size <= 40.dp -> 16.dp
        size <= 64.dp -> 20.dp
        else -> 24.dp
    }

    fun badgeIconSizeForAvatar(size: Dp): Dp = when {
        size <= 28.dp -> 8.dp
        size <= 40.dp -> 10.dp
        size <= 64.dp -> 12.dp
        else -> 14.dp
    }
}

@Composable
fun NexusAvatar(
    id: Int,
    name: String?,
    avatarUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    badge: NexusAvatarBadge? = null,
    indicator: NexusAvatarIndicator? = null
) {
    val radius = NexusAvatarDefaults.radiusForSize(size)
    val shape = RoundedCornerShape(radius)
    val fallbackColor = NexusAvatarDefaults.colorForId(id)
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
                avatarSize = size,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }

        if (indicator == NexusAvatarIndicator.Owner) {
            AvatarIndicator(
                avatarSize = size,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
    }
}

@Composable
private fun AvatarBadge(
    badge: NexusAvatarBadge,
    avatarSize: Dp,
    modifier: Modifier = Modifier
) {
    val icon = when (badge) {
        NexusAvatarBadge.Agent -> Icons.Filled.SmartToy
        NexusAvatarBadge.SystemAgent -> Icons.Filled.VerifiedUser
        NexusAvatarBadge.Group -> Icons.Filled.Groups
    }
    val backgroundModifier = when (badge) {
        NexusAvatarBadge.SystemAgent -> Modifier.background(
            Brush.linearGradient(listOf(Color(0xFF3B82F6), Color(0xFF4F46E5)))
        )
        NexusAvatarBadge.Agent,
        NexusAvatarBadge.Group -> Modifier.background(MaterialTheme.colorScheme.primary)
    }
    val badgeSize = NexusAvatarDefaults.badgeSizeForAvatar(avatarSize)
    val iconSize = NexusAvatarDefaults.badgeIconSizeForAvatar(avatarSize)

    Box(
        modifier = modifier
            .size(badgeSize)
            .clip(RoundedCornerShape(50))
            .then(backgroundModifier)
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
            modifier = Modifier.size(iconSize),
            tint = Color.White
        )
    }
}

@Composable
private fun AvatarIndicator(
    avatarSize: Dp,
    modifier: Modifier = Modifier
) {
    val badgeSize = NexusAvatarDefaults.badgeSizeForAvatar(avatarSize)
    val iconSize = NexusAvatarDefaults.badgeIconSizeForAvatar(avatarSize)
    Box(
        modifier = modifier
            .size(badgeSize)
            .clip(RoundedCornerShape(50))
            .background(Color(0xFFF59E0B))
            .border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(50)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.MilitaryTech,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = Color.White
        )
    }
}
