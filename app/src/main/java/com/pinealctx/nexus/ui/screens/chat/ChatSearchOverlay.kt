package com.pinealctx.nexus.ui.screens.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pinealctx.nexus.R
import com.pinealctx.nexus.core.MessageSearchResultData

@Composable
fun ChatSearchOverlay(
    state: ChatSearchState,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onResultClick: (MessageSearchResultData) -> Unit
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize()) {
            Text(
                stringResource(R.string.chat_search_local_hint),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(Modifier.fillMaxSize()) {
                if (!state.isLoading && state.results.isEmpty() && !state.failed) {
                    item {
                        Text(
                            stringResource(if (state.hasSearched) R.string.chat_search_empty else R.string.chat_search_prompt),
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }
                items(state.results, key = { it.messageId }) { result ->
                    val snippet = searchResultSnippet(result.textSnippet, state.query.trim())
                    val match = snippet.indexOf(state.query.trim(), ignoreCase = true)
                    val highlight = MaterialTheme.colorScheme.primary
                    ListItem(
                        headlineContent = {
                            Text(
                                buildAnnotatedString {
                                    append(snippet)
                                    if (match >= 0 && state.query.isNotBlank()) {
                                        addStyle(SpanStyle(color = highlight, fontWeight = FontWeight.Bold), match, match + state.query.trim().length)
                                    }
                                }, maxLines = 3, overflow = TextOverflow.Ellipsis
                            )
                        },
                        supportingContent = { Text(formatSearchTime(result.createdAt)) },
                        modifier = Modifier.clickable { onResultClick(result) }
                    )
                    HorizontalDivider()
                }
                if (state.failed) {
                    item {
                        TextButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.chat_search_retry))
                        }
                    }
                } else if (state.hasMore) {
                    item {
                        TextButton(onClick = onLoadMore, enabled = !state.isLoading, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.chat_search_more))
                        }
                    }
                }
            }
        }
    }
}

internal fun searchResultSnippet(text: String, query: String): String {
    val match = text.indexOf(query, ignoreCase = true).coerceAtLeast(0)
    var start = (match - 48).coerceAtLeast(0)
    var end = (start + maxOf(180, query.length + 48)).coerceAtMost(text.length)
    if (start > 0 && text[start].isLowSurrogate()) start--
    if (end < text.length && end > 0 && text[end - 1].isHighSurrogate()) end++
    return (if (start > 0) "…" else "") + text.substring(start, end) + if (end < text.length) "…" else ""
}

private fun formatSearchTime(millis: Long): String =
    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(millis))
