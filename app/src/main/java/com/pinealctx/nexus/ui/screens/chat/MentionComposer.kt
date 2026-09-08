package com.pinealctx.nexus.ui.screens.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.pinealctx.nexus.R
import com.pinealctx.nexus.core.TextEntityData
import com.pinealctx.nexus.core.adjustTextEntities
import com.shared.v1.MessageEntityType

data class MentionCandidate(val userId: Int, val name: String, val isAll: Boolean = false)
internal data class MentionQuery(val start: Int, val end: Int, val filter: String)

internal fun mentionQuery(value: TextFieldValue): MentionQuery? {
    if (!value.selection.collapsed || value.composition != null) return null
    val end = value.selection.start
    val start = value.text.lastIndexOf('@', (end - 1).coerceAtLeast(0))
    if (start < 0 || start >= end || (start > 0 && !value.text[start - 1].isWhitespace())) return null
    val filter = value.text.substring(start + 1, end)
    if (filter.any { it.isWhitespace() || it == '@' }) return null
    return MentionQuery(start, end, filter)
}

internal fun insertMention(
    value: TextFieldValue,
    entities: List<TextEntityData>,
    candidate: MentionCandidate
): Pair<TextFieldValue, List<TextEntityData>> {
    val query = mentionQuery(value) ?: return value to entities
    val label = "@${candidate.name}"
    val text = value.text.replaceRange(query.start, query.end, "$label ")
    val updated = adjustTextEntities(value.text, text, entities) + TextEntityData(
        MessageEntityType.MESSAGE_ENTITY_TYPE_MENTION, query.start, label.length,
        userId = candidate.userId, isAll = candidate.isAll
    )
    return TextFieldValue(text, TextRange(query.start + label.length + 1)) to updated
}

@Composable
internal fun MentionSuggestions(
    candidates: List<MentionCandidate>,
    query: MentionQuery,
    loading: Boolean,
    failed: Boolean,
    onRetry: () -> Unit,
    onSelect: (MentionCandidate) -> Unit
) {
    val matches = candidates.filter { it.name.contains(query.filter, ignoreCase = true) }
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.chat_mention_members), style = MaterialTheme.typography.labelMedium)
            when {
                loading -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 12.dp))
                failed -> TextButton(onClick = onRetry) { Text(stringResource(R.string.chat_mention_retry)) }
                matches.isEmpty() -> Text(stringResource(R.string.chat_mention_empty), modifier = Modifier.padding(12.dp))
                else -> LazyColumn(Modifier.heightIn(max = 180.dp)) {
                    items(matches, key = { "${it.userId}:${it.isAll}" }) { candidate ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onSelect(candidate) }.padding(vertical = 8.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            com.pinealctx.nexus.ui.components.NexusAvatar(
                                id = candidate.userId, name = candidate.name, avatarUrl = null, size = 32.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("@${candidate.name}", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}
