package com.pinealctx.nexus.ui.components

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class EmojiCategory(
    val icon: String,
    val emojis: List<String>
)

private val categories = listOf(
    EmojiCategory("😀", listOf(
        "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂", "🙂", "🙃",
        "😉", "😊", "😇", "🥰", "😍", "🤩", "😘", "😗", "😚", "😙",
        "🥲", "😋", "😛", "😜", "🤪", "😝", "🤑", "🤗", "🤭", "🫢",
        "🤫", "🤔", "🫡", "🤐", "🤨", "😐", "😑", "😶", "🫥", "😏",
        "😒", "🙄", "😬", "🤥", "😌", "😔", "😪", "🤤", "😴", "😷",
        "🤒", "🤕", "🤢", "🤮", "🥵", "🥶", "🥴", "😵", "🤯", "🤠",
        "🥳", "🥸", "😎", "🤓", "🧐", "😕", "🫤", "😟", "🙁", "😮",
        "😯", "😲", "😳", "🥺", "🥹", "😦", "😧", "😨", "😰", "😥",
        "😢", "😭", "😱", "😖", "😣", "😞", "😓", "😩", "😫", "🥱",
        "😤", "😡", "😠", "🤬", "😈", "👿", "💀", "☠️", "💩", "🤡",
        "👹", "👺", "👻", "👽", "👾", "🤖"
    )),
    EmojiCategory("👋", listOf(
        "👋", "🤚", "🖐️", "✋", "🖖", "🫱", "🫲", "🫳", "🫴", "👌",
        "🤌", "🤏", "✌️", "🤞", "🫰", "🤟", "🤘", "🤙", "👈", "👉",
        "👆", "🖕", "👇", "☝️", "🫵", "👍", "👎", "✊", "👊", "🤛",
        "🤜", "👏", "🙌", "🫶", "👐", "🤲", "🤝", "🙏", "✍️", "💅",
        "🤳", "💪", "🦾", "🦿", "🦵", "🦶", "👂", "🦻", "👃", "🧠",
        "👀", "👁️", "👅", "👄", "🫦"
    )),
    EmojiCategory("❤️", listOf(
        "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔",
        "❤️‍🔥", "❤️‍🩹", "❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝",
        "💟", "💋", "💌", "💐", "🌹", "🥀", "🌺", "🌸", "🌼", "🌻",
        "🌷", "💍", "💎", "🎁", "🎀", "🎊", "🎉", "🎈", "🎂", "🍰"
    )),
    EmojiCategory("🐶", listOf(
        "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐻‍❄️", "🐨",
        "🐯", "🦁", "🐮", "🐷", "🐽", "🐸", "🐵", "🙈", "🙉", "🙊",
        "🐒", "🐔", "🐧", "🐦", "🐤", "🐣", "🐥", "🦆", "🦅", "🦉",
        "🦇", "🐺", "🐗", "🐴", "🦄", "🐝", "🐛", "🦋", "🐌", "🐞",
        "🐜", "🐢", "🐍", "🦎", "🦖", "🦕", "🐙", "🦑", "🦐", "🦞",
        "🦀", "🐡", "🐠", "🐟", "🐬", "🐳", "🐋", "🦈", "🐊", "🐅",
        "🐆", "🦓", "🦍", "🐘", "🦛", "🦏", "🐪", "🐫", "🦒", "🦘"
    )),
    EmojiCategory("🍔", listOf(
        "🍏", "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🫐",
        "🍈", "🍒", "🍑", "🥭", "🍍", "🥥", "🥝", "🍅", "🍆", "🥑",
        "🥦", "🥬", "🥒", "🌶️", "🌽", "🥕", "🥔", "🍠", "🥐", "🍞",
        "🥖", "🥨", "🧀", "🥚", "🍳", "🥞", "🧇", "🥓", "🥩", "🍗",
        "🍖", "🌭", "🍔", "🍟", "🍕", "🥪", "🥙", "🌮", "🌯", "🥗",
        "🍝", "🍜", "🍲", "🍛", "🍣", "🍱", "🥟", "🍤", "🍙", "🍚",
        "🍘", "🍥", "🍢", "🍡", "🍧", "🍨", "🍦", "🥧", "🧁", "🍰",
        "🎂", "🍮", "🍭", "🍬", "🍫", "🍿", "🍩", "🍪", "☕", "🍵"
    )),
    EmojiCategory("⚽", listOf(
        "⚽", "🏀", "🏈", "⚾", "🥎", "🎾", "🏐", "🏉", "🥏", "🎱",
        "🏓", "🏸", "🏒", "🏑", "🥍", "🏏", "🥅", "⛳", "🏹", "🎣",
        "🥊", "🥋", "🎽", "🛹", "🛼", "🛷", "⛸️", "🥌", "🎿", "🏂",
        "🏋️", "🤸", "⛹️", "🤺", "🤾", "🏌️", "🏇", "🧘", "🏄", "🏊",
        "🚣", "🧗", "🚵", "🚴", "🏆", "🥇", "🥈", "🥉", "🏅", "🎖️",
        "🎫", "🎪", "🤹", "🎭", "🎨", "🎬", "🎤", "🎧", "🎼", "🎹",
        "🥁", "🎷", "🎺", "🎸", "🪕", "🎻", "🎲", "♟️", "🎯", "🎳"
    )),
    EmojiCategory("🚗", listOf(
        "🚗", "🚕", "🚙", "🚌", "🚎", "🏎️", "🚓", "🚑", "🚒", "🚐",
        "🛻", "🚚", "🚛", "🚜", "🏍️", "🛵", "🚲", "🛴", "🚔", "🚍",
        "🚘", "🚖", "🚡", "🚠", "🚟", "🚃", "🚋", "🚞", "🚝", "🚄",
        "🚅", "🚈", "🚂", "🚆", "🚇", "🚊", "🚉", "✈️", "🛫", "🛬",
        "🚀", "🛸", "🚁", "🛶", "⛵", "🚤", "🛳️", "🚢", "🏠", "🏡",
        "🏢", "🏣", "🏤", "🏥", "🏦", "🏨", "🏩", "🏪", "🏫", "🏬",
        "🏭", "🏯", "🏰", "💒", "🗼", "🗽", "⛪", "🕌", "🛕", "🕍"
    )),
    EmojiCategory("💡", listOf(
        "⌚", "📱", "💻", "⌨️", "🖥️", "🖨️", "🖱️", "💽", "💾", "💿",
        "📀", "📷", "📸", "📹", "🎥", "📞", "☎️", "📺", "📻", "🎙️",
        "⏰", "⌛", "⏳", "📡", "🔋", "🔌", "💡", "🔦", "🕯️", "💸",
        "💵", "💴", "💶", "💷", "💰", "💳", "💎", "⚖️", "🔧", "🔨",
        "⚒️", "🛠️", "⛏️", "🔩", "⚙️", "🔫", "💣", "🔪", "🗡️", "⚔️",
        "🛡️", "🔑", "🗝️", "🔒", "🔓", "📦", "📫", "📬", "📭", "📮",
        "📝", "📁", "📂", "📅", "📆", "📌", "📎", "🖇️", "📏", "📐",
        "✂️", "🗑️", "🔍", "🔎", "🔬", "🔭", "📰", "🏷️", "🔖", "💰"
    )),
    EmojiCategory("🏁", listOf(
        "🏳️", "🏴", "🏁", "🚩", "🏳️‍🌈", "🏴‍☠️", "🇨🇳", "🇺🇸", "🇬🇧", "🇯🇵",
        "🇰🇷", "🇩🇪", "🇫🇷", "🇪🇸", "🇮🇹", "🇷🇺", "🇧🇷", "🇮🇳", "🇦🇺", "🇨🇦",
        "✅", "❌", "❓", "❗", "‼️", "⁉️", "💯", "🔥", "✨", "⭐",
        "🌟", "💫", "⚡", "💥", "🎵", "🎶", "➕", "➖", "➗", "✖️",
        "♾️", "💲", "©️", "®️", "™️", "🔴", "🟠", "🟡", "🟢", "🔵",
        "🟣", "⚫", "⚪", "🟤", "🔶", "🔷", "🔸", "🔹", "🔺", "🔻",
        "▪️", "▫️", "◾", "◽", "◼️", "◻️", "⬛", "⬜", "🟥", "🟧"
    ))
)

private const val RECENT_PREFS = "nexus_emoji_recent"
private const val RECENT_KEY = "recent_emojis"
private const val MAX_RECENT = 32

@Composable
fun EmojiPicker(
    onEmojiSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(RECENT_PREFS, Context.MODE_PRIVATE) }
    var recentEmojis by remember {
        mutableStateOf(prefs.getString(RECENT_KEY, "")?.split(",")?.filter { it.isNotEmpty() } ?: emptyList())
    }
    var selectedTab by remember { mutableIntStateOf(if (recentEmojis.isNotEmpty()) 0 else 1) }

    val allTabs = buildList {
        if (recentEmojis.isNotEmpty()) {
            add(EmojiCategory("🕐", recentEmojis))
        }
        addAll(categories)
    }

    Column(modifier = modifier.fillMaxWidth().height(300.dp)) {
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            allTabs.forEachIndexed { index, category ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(category.icon, fontSize = 18.sp) }
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(8),
            contentPadding = PaddingValues(4.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(allTabs[selectedTab].emojis) { emoji ->
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clickable {
                            onEmojiSelected(emoji)
                            val updated = (listOf(emoji) + recentEmojis.filter { it != emoji }).take(MAX_RECENT)
                            recentEmojis = updated
                            prefs.edit().putString(RECENT_KEY, updated.joinToString(",")).apply()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = emoji, fontSize = 24.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
