package com.pinealctx.nexus.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val EMOJI_LIST = listOf(
    // Smileys
    "😀", "😃", "😄", "😁", "😆",
    "😅", "😂", "🤣", "😊", "😇",
    "🙂", "🙃", "😉", "😌", "😍",
    "🥰", "😘", "😗", "😙", "😚",
    "😋", "😛", "😜", "🤪", "😝",
    "🤑", "🤗", "🤭", "🤫", "🤔",
    "🤐", "🤨", "😐", "😑", "😶",
    "😏", "😒", "🙄", "😬", "🤥",
    // Gestures
    "👍", "👎", "👊", "✊", "🤛",
    "🤜", "👏", "🙌", "👋", "🤚",
    "👌", "✌️", "🤞", "🤟", "🤘",
    "👈", "👉", "👆", "👇", "☝️",
    // Hearts
    "❤️", "🧡", "💛", "💚", "💙",
    "💜", "🖤", "💔", "❣️", "💕",
    // Objects
    "🔥", "⭐", "🌟", "💯", "🎉",
    "🎊", "💥", "💫", "💢", "💦"
)

@Composable
fun EmojiPicker(
    onEmojiSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(250.dp),
        tonalElevation = 2.dp
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(8),
            contentPadding = PaddingValues(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(EMOJI_LIST) { emoji ->
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clickable { onEmojiSelected(emoji) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = emoji,
                        fontSize = 24.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
