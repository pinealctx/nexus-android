package com.pinealctx.nexus.ui.screens.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.pinealctx.nexus.core.MessageSearchResultData

@Composable
fun ChatSearchOverlay(
    searchResults: List<MessageSearchResultData>,
    isLocatingMessage: Boolean,
    onResultClick: (MessageSearchResultData) -> Unit
) {
    if (searchResults.isEmpty()) return

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (isLocatingMessage) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(searchResults) { result ->
                    ListItem(
                        headlineContent = {
                            Text(
                                text = result.textSnippet.replace("«", "").replace("»", ""),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        supportingContent = {
                            Text(
                                text = formatSearchTime(result.createdAt),
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        modifier = Modifier.clickable { onResultClick(result) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

private fun formatSearchTime(millis: Long): String {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(millis))
}
