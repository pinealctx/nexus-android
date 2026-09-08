package com.pinealctx.nexus.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.pinealctx.nexus.core.MessageContent
import com.pinealctx.nexus.core.safeWebLink
import com.pinealctx.nexus.core.withDetectedLinks
import com.shared.v1.MessageEntityType.*

@Composable
fun RichMessageText(content: MessageContent.Text, onMentionClick: (Int) -> Unit) {
    val uriHandler = LocalUriHandler.current
    val accent = MaterialTheme.colorScheme.primary
    val annotated = buildAnnotatedString {
        append(content.text)
        val linkedRanges = mutableListOf<IntRange>()
        withDetectedLinks(content.text, content.entities).forEach { entity ->
            val start = entity.offset
            val end = start + entity.length
            val label = content.text.substring(start, end)
            val style = when (entity.type) {
                MESSAGE_ENTITY_TYPE_BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                MESSAGE_ENTITY_TYPE_ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                MESSAGE_ENTITY_TYPE_CODE -> SpanStyle(fontFamily = FontFamily.Monospace)
                MESSAGE_ENTITY_TYPE_MENTION -> SpanStyle(color = accent, fontWeight = FontWeight.Medium)
                MESSAGE_ENTITY_TYPE_HASHTAG -> SpanStyle(color = accent)
                else -> SpanStyle()
            }
            addStyle(style, start, end)
            val destination = when (entity.type) {
                MESSAGE_ENTITY_TYPE_URL -> safeWebLink(entity.value.ifBlank { label })
                MESSAGE_ENTITY_TYPE_EMAIL -> label.takeIf { it.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) }?.let { "mailto:$it" }
                MESSAGE_ENTITY_TYPE_PHONE -> entity.value.ifBlank { label }
                    .takeIf { it.matches(Regex("[+0-9() .-]+")) }?.let { "tel:$it" }
                else -> null
            }
            val mention = entity.type == MESSAGE_ENTITY_TYPE_MENTION && !entity.isAll && entity.userId > 0
            if ((destination != null || mention) && linkedRanges.none { start < it.last + 1 && end > it.first }) {
                linkedRanges.add(start until end)
                addLink(
                    LinkAnnotation.Clickable(
                        tag = "entity:$start",
                        styles = TextLinkStyles(SpanStyle(color = accent, textDecoration = TextDecoration.Underline)),
                        linkInteractionListener = {
                            if (mention) onMentionClick(entity.userId)
                            else destination?.let { runCatching { uriHandler.openUri(it) } }
                        }
                    ), start, end
                )
            }
        }
    }
    Text(text = annotated, style = MaterialTheme.typography.bodyMedium)
}
