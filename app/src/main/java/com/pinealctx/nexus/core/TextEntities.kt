package com.pinealctx.nexus.core

import com.shared.v1.MessageEntityType
import java.net.URI

data class TextEntityData(
    val type: MessageEntityType,
    val offset: Int,
    val length: Int,
    val userId: Int = 0,
    val isAll: Boolean = false,
    val value: String = ""
)

// Kotlin and desktop JavaScript both index strings in UTF-16 code units.
fun validTextEntities(text: String, entities: List<TextEntityData>): List<TextEntityData> =
    entities.filter { entity ->
        entity.offset >= 0 && entity.length > 0 && entity.offset <= text.length &&
            entity.length <= text.length - entity.offset &&
            !splitsSurrogate(text, entity.offset) &&
            !splitsSurrogate(text, entity.offset + entity.length)
    }.distinct().sortedBy { it.offset }

private fun splitsSurrogate(text: String, index: Int): Boolean =
    index in 1 until text.length && text[index].isLowSurrogate() && text[index - 1].isHighSurrogate()

fun safeWebLink(value: String): String? {
    val candidate = if (value.startsWith("www.", ignoreCase = true)) "https://$value" else value
    return runCatching { URI(candidate) }.getOrNull()?.let { uri ->
        candidate.takeIf {
            uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank()
        }
    }
}

fun withDetectedLinks(text: String, entities: List<TextEntityData>): List<TextEntityData> {
    val valid = validTextEntities(text, entities)
    val links = Regex("(?:https?://|www\\.)[^\\s<>]+", RegexOption.IGNORE_CASE)
        .findAll(text).mapNotNull { match ->
            val label = match.value.trimEnd('.', ',', '!', '?', ';', ':', ')', ']', '}', '。', '，', '！', '？')
            val url = safeWebLink(label) ?: return@mapNotNull null
            val start = match.range.first
            if (valid.any { it.offset < start + label.length && it.offset + it.length > start }) {
                return@mapNotNull null
            }
            TextEntityData(MessageEntityType.MESSAGE_ENTITY_TYPE_URL, start, label.length, value = url)
        }.toList()
    return (valid + links).sortedBy { it.offset }
}

// Keep identity only for spans untouched by the edit; typing a label never invents a mention.
fun adjustTextEntities(old: String, new: String, entities: List<TextEntityData>, editStartHint: Int? = null): List<TextEntityData> {
    if (old == new) return validTextEntities(new, entities)
    val commonPrefix = old.commonPrefixWith(new).length
    val prefix = editStartHint?.coerceIn(0, commonPrefix) ?: commonPrefix
    val suffix = old.substring(prefix).commonSuffixWith(new.substring(prefix)).length
    val oldEnd = old.length - suffix
    val shift = new.length - old.length
    return validTextEntities(new, entities.mapNotNull { entity ->
        when {
            entity.offset + entity.length <= prefix -> entity
            entity.offset >= oldEnd -> entity.copy(offset = entity.offset + shift)
            else -> null
        }
    })
}
