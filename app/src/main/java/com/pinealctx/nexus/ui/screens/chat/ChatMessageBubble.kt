package com.pinealctx.nexus.ui.screens.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.pinealctx.nexus.R
import com.pinealctx.nexus.core.MessageContent
import com.pinealctx.nexus.core.MessageSendState
import com.pinealctx.nexus.core.previewText

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: ChatMessageItem,
    currentUserId: Int = 0,
    pendingActionMessageId: Long? = null,
    onReply: (ChatMessageItem.Remote) -> Unit = {},
    onEdit: (ChatMessageItem.Remote) -> Unit = {},
    onRecall: (ChatMessageItem.Remote) -> Unit = {},
    onDelete: (ChatMessageItem.Remote) -> Unit = {},
    onCopy: (String) -> Unit = {},
    onRetry: (Long) -> Unit = {},
    onImageClick: (String) -> Unit = {}
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val remoteMessage = message as? ChatMessageItem.Remote
    val actionState = ChatMessageActionPolicy.forMessage(
        message = message,
        currentUserId = currentUserId,
        pendingActionMessageId = pendingActionMessageId
    )
    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        if (remoteMessage != null && !actionState.isPending) {
                            menuExpanded = true
                        }
                    }
                )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "User ${message.senderId}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                message.replyToMessageId?.let { replyToMessageId ->
                    Spacer(modifier = Modifier.height(4.dp))
                    ReplyReferenceLabel(replyToMessageId, message.replyPreview)
                }
                Spacer(modifier = Modifier.height(4.dp))
                MessageContentView(content = message.content, recalled = message.recalled, onImageClick = onImageClick)
                MessageSendStateLabel(message, onRetry)
                if (message.edited) {
                    Text(
                        text = stringResource(R.string.message_edited),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = FontStyle.Italic
                    )
                }
            }
        }
        if (remoteMessage != null) {
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                if (actionState.canCopy) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_action_copy)) },
                        onClick = {
                            menuExpanded = false
                            actionState.copyText?.let(onCopy)
                        }
                    )
                }
                if (!remoteMessage.recalled) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_action_reply)) },
                        enabled = actionState.canReply,
                        onClick = {
                            menuExpanded = false
                            onReply(remoteMessage)
                        }
                    )
                }
                if (actionState.canEdit) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_action_edit)) },
                        onClick = {
                            menuExpanded = false
                            onEdit(remoteMessage)
                        }
                    )
                }
                if (remoteMessage.senderId == currentUserId && !remoteMessage.recalled) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (actionState.isPending) {
                                    stringResource(R.string.chat_action_recalling)
                                } else {
                                    stringResource(R.string.chat_action_recall)
                                }
                            )
                        },
                        enabled = actionState.canRecall,
                        onClick = {
                            menuExpanded = false
                            onRecall(remoteMessage)
                        }
                    )
                }
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (actionState.isPending) {
                                stringResource(R.string.chat_action_deleting)
                            } else {
                                stringResource(R.string.chat_action_delete)
                            },
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    enabled = actionState.canDelete,
                    onClick = {
                        menuExpanded = false
                        onDelete(remoteMessage)
                    }
                )
            }
        }
    }
}

@Composable
fun ReplyComposerPreview(message: ChatMessageItem.Remote, onClear: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.chat_replying_to, message.senderId),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = message.content.previewText(),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.chat_cancel_reply))
            }
        }
    }
}

@Composable
fun EditComposerPreview(message: ChatMessageItem.Remote, onClear: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.chat_editing_message, message.data.messageId),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = message.content.previewText(),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.chat_cancel_edit))
            }
        }
    }
}

@Composable
private fun ReplyReferenceLabel(replyToMessageId: Long, preview: ChatReplyPreview?) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(
                text = if (preview == null) {
                    stringResource(R.string.chat_reply_reference, replyToMessageId)
                } else if (preview.senderNickname.isNotBlank()) {
                    preview.senderNickname
                } else {
                    stringResource(R.string.chat_reply_preview_sender, preview.senderId)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            preview?.let {
                Text(
                    text = it.text,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MessageSendStateLabel(message: ChatMessageItem, onRetry: (Long) -> Unit) {
    val sendState = message.sendState
    val label = when (sendState) {
        MessageSendState.SENDING -> stringResource(R.string.chat_send_state_sending)
        MessageSendState.FAILED -> stringResource(R.string.chat_send_state_failed)
        MessageSendState.SENT,
        null -> return
    }
    Spacer(modifier = Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (sendState == MessageSendState.FAILED) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        if (sendState == MessageSendState.FAILED && message is ChatMessageItem.Local) {
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(
                onClick = { onRetry(message.data.clientMessageId) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(
                    text = stringResource(R.string.chat_send_retry),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
fun MessageContentView(content: MessageContent, recalled: Boolean, onImageClick: (String) -> Unit = {}) {
    if (recalled) {
        Text(
            text = stringResource(R.string.message_recalled),
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    when (content) {
        is MessageContent.Text -> {
            Text(
                text = content.text,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        is MessageContent.Image -> {
            ImageBubble(fileId = content.fileId, width = content.width, height = content.height, onClick = { onImageClick(content.fileId) })
        }
        is MessageContent.File -> {
            FileBubble(name = content.name, size = content.size)
        }
        is MessageContent.Markdown -> {
            MarkdownBubble(text = content.text)
        }
        is MessageContent.Card -> {
            CardBubble(json = content.json)
        }
        is MessageContent.Audio -> {
            AudioBubble(duration = content.duration)
        }
        is MessageContent.Video -> {
            VideoBubble(fileId = content.fileId, duration = content.duration)
        }
        is MessageContent.Recalled -> {
            Text(
                text = stringResource(R.string.message_recalled),
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        is MessageContent.Unknown -> {
            Text(
                text = stringResource(R.string.message_unsupported),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ImageBubble(fileId: String, width: Int, height: Int, onClick: () -> Unit = {}) {
    val aspectRatio = if (height > 0) width.toFloat() / height.toFloat() else 1f
    val displayWidth = 200.dp
    AsyncImage(
        model = fileId,
        contentDescription = null,
        modifier = Modifier
            .widthIn(max = displayWidth)
            .aspectRatio(aspectRatio.coerceIn(0.5f, 2f))
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentScale = ContentScale.Crop
    )
}

@Composable
fun FileBubble(name: String, size: Long) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatFileSize(size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun MarkdownBubble(text: String) {
    com.pinealctx.nexus.ui.components.MarkdownText(text = text)
}

@Composable
fun CardBubble(json: String) {
    com.pinealctx.nexus.ui.components.AdaptiveCardView(json = json)
}

@Composable
fun AudioBubble(duration: Int) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Image,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${duration}s",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun VideoBubble(fileId: String, duration: Int) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.size(200.dp, 150.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "Video (${duration}s)",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}
