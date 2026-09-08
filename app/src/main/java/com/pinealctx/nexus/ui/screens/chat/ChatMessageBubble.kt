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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.pinealctx.nexus.R
import com.pinealctx.nexus.core.MessageContent
import com.pinealctx.nexus.core.GroupEventType
import com.pinealctx.nexus.core.MessageSendState
import com.pinealctx.nexus.core.MessageStreamPhase
import com.pinealctx.nexus.core.previewText
import com.pinealctx.nexus.ui.components.AudioMessagePlayer
import com.pinealctx.nexus.ui.components.ChatMediaController
import com.pinealctx.nexus.ui.components.NexusAvatar
import com.pinealctx.nexus.ui.components.VideoMessagePlayer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: ChatMessageItem,
    currentUserId: Int = 0,
    senderName: String? = null,
    senderAvatarUrl: String? = null,
    groupMemberNames: Map<Int, String> = emptyMap(),
    showSenderName: Boolean = false,
    showSenderAvatar: Boolean = false,
    reserveSenderAvatarSpace: Boolean = false,
    pendingActionMessageId: Long? = null,
    onReply: (ChatMessageItem.Remote) -> Unit = {},
    onEdit: (ChatMessageItem.Remote) -> Unit = {},
    onRecall: (ChatMessageItem.Remote) -> Unit = {},
    onDelete: (ChatMessageItem.Remote) -> Unit = {},
    onCopy: (String) -> Unit = {},
    onRetry: (Long) -> Unit = {},
    mediaUrls: Map<String, String> = emptyMap(),
    mediaController: ChatMediaController? = null,
    onMediaNeeded: (String) -> Unit = {},
    onImageClick: (String) -> Unit = {},
    onOpenMedia: (String) -> Unit = {},
    onCardAction: (Long, String, String) -> Unit = { _, _, _ -> },
    onOpenMiniApp: (Int, String) -> Unit = { _, _ -> },
    onMentionClick: (Int) -> Unit = {}
) {
    if (message.senderId <= 0) {
        SystemMessageBubble(message, groupMemberNames)
        return
    }

    var menuExpanded by remember { mutableStateOf(false) }
    val remoteMessage = message as? ChatMessageItem.Remote
    val isSelf = message.senderId == currentUserId
    val actionState = ChatMessageActionPolicy.forMessage(
        message = message,
        currentUserId = currentUserId,
        pendingActionMessageId = pendingActionMessageId
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        contentAlignment = if (isSelf) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.Top) {
            val resolvedSenderName = senderName ?: stringResource(R.string.chat_user_fallback, message.senderId)
            if ((showSenderAvatar || reserveSenderAvatarSpace) && !isSelf) {
                if (showSenderAvatar) {
                    NexusAvatar(
                        id = message.senderId,
                        name = resolvedSenderName,
                        avatarUrl = senderAvatarUrl,
                        size = 32.dp,
                        modifier = Modifier.padding(top = if (showSenderName) 18.dp else 0.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.width(32.dp))
                }
                Spacer(modifier = Modifier.width(7.dp))
            }
            Column(horizontalAlignment = if (isSelf) Alignment.End else Alignment.Start) {
                if (showSenderName && !isSelf) {
                    Text(
                        text = resolvedSenderName,
                        modifier = Modifier.padding(start = 10.dp, bottom = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                modifier = Modifier
                    .widthIn(max = 304.dp)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            if (remoteMessage != null && !actionState.isPending) {
                                menuExpanded = true
                            }
                        }
                    ),
                shape = if (isSelf) {
                    RoundedCornerShape(18.dp, 5.dp, 18.dp, 18.dp)
                } else {
                    RoundedCornerShape(5.dp, 18.dp, 18.dp, 18.dp)
                },
                color = if (isSelf) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                    message.replyToMessageId?.let { replyToMessageId ->
                        ReplyReferenceLabel(replyToMessageId, message.replyPreview)
                        Spacer(modifier = Modifier.height(5.dp))
                    }
                    MessageContentView(
                        onMentionClick = onMentionClick,
                        content = message.content,
                        recalled = message.recalled,
                        mediaUrl = message.content.fileIdOrNull?.let(mediaUrls::get),
                        resolvedMediaUrls = mediaUrls,
                        thumbnailUrl = (message.content as? MessageContent.Video)
                            ?.thumbnailFileId
                            ?.takeIf { it.isNotBlank() }
                            ?.let(mediaUrls::get),
                        mediaController = mediaController,
                        onMediaNeeded = onMediaNeeded,
                        onImageClick = onImageClick,
                        onOpenMedia = onOpenMedia,
                        onCardAction = { type, data ->
                            remoteMessage?.data?.messageId?.let { onCardAction(it, type, data) }
                        },
                        onOpenMiniApp = { agentUserId, startParam ->
                            onOpenMiniApp(agentUserId.takeIf { it > 0 } ?: message.senderId, startParam)
                        }
                    )
                    MessageSendStateLabel(message, onRetry)
                    }
                }
                Row(
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (message.edited) {
                        Text(
                            text = stringResource(R.string.message_edited),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontStyle = FontStyle.Italic
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = message.createdAt.formatMessageTime(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
private fun SystemMessageBubble(message: ChatMessageItem, memberNames: Map<Int, String>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        ) {
            Text(
                text = when (val content = message.content) {
                    is MessageContent.GroupEvent -> groupEventText(content, memberNames)
                    MessageContent.Unknown -> stringResource(R.string.message_unsupported)
                    else -> content.previewText()
                },
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
fun MessageContentView(
    content: MessageContent,
    recalled: Boolean,
    mediaUrl: String? = null,
    resolvedMediaUrls: Map<String, String> = emptyMap(),
    thumbnailUrl: String? = null,
    mediaController: ChatMediaController? = null,
    onMediaNeeded: (String) -> Unit = {},
    onImageClick: (String) -> Unit = {},
    onOpenMedia: (String) -> Unit = {},
    onCardAction: (String, String) -> Unit = { _, _ -> },
    onOpenMiniApp: (Int, String) -> Unit = { _, _ -> },
    onMentionClick: (Int) -> Unit = {}
) {
    if (recalled) {
        Text(
            text = stringResource(R.string.message_recalled),
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    content.fileIds.forEach { fileId ->
        LaunchedEffect(fileId) { onMediaNeeded(fileId) }
    }

    when (content) {
        is MessageContent.Text -> {
            com.pinealctx.nexus.ui.components.RichMessageText(content, onMentionClick)
        }
        is MessageContent.Image -> {
            ImageBubble(
                model = mediaUrl,
                width = content.width,
                height = content.height,
                onClick = { onImageClick(content.fileId) }
            )
        }
        is MessageContent.File -> {
            FileBubble(name = content.name, size = content.size, onClick = { onOpenMedia(content.fileId) })
        }
        is MessageContent.Markdown -> {
            MarkdownBubble(text = content.text)
        }
        is MessageContent.Card -> {
            CardBubble(
                json = content.json,
                fallbackText = content.fallbackText,
                mediaController = mediaController,
                resolvedMediaUrls = resolvedMediaUrls,
                onMediaNeeded = onMediaNeeded,
                onAction = onCardAction,
                onOpenMiniApp = onOpenMiniApp
            )
        }
        is MessageContent.Stream -> StreamMessageBubble(content)
        is MessageContent.GroupEvent -> {
            Text(
                text = stringResource(R.string.message_group_update),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        is MessageContent.Audio -> {
            AudioMessagePlayer(
                mediaId = content.fileId,
                url = mediaUrl,
                declaredDurationMs = content.durationMs,
                transcript = content.transcript,
                controller = mediaController
            )
        }
        is MessageContent.Video -> {
            VideoMessagePlayer(
                mediaId = content.fileId,
                url = mediaUrl,
                thumbnailUrl = thumbnailUrl,
                declaredDurationMs = content.durationMs,
                width = content.width,
                height = content.height,
                controller = mediaController
            )
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
private fun StreamMessageBubble(content: MessageContent.Stream) {
    val isActive = !content.phase.isTerminal
    Column {
        if (content.accumulatedText.isNotBlank()) {
            if (!streamContentUsesMarkdown(content.contentType)) {
                Text(
                    text = content.accumulatedText,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                MarkdownBubble(text = content.accumulatedText)
            }
        }
        if (isActive) {
            Row(
                modifier = Modifier.padding(top = if (content.accumulatedText.isBlank()) 1.dp else 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(13.dp),
                    strokeWidth = 1.8.dp
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = stringResource(R.string.message_stream_generating),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (content.phase == MessageStreamPhase.ERROR) {
            Text(
                text = content.errorMessage?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.message_stream_interrupted),
                modifier = Modifier.padding(top = if (content.accumulatedText.isBlank()) 0.dp else 7.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

internal fun streamContentUsesMarkdown(contentType: String): Boolean =
    contentType
        .substringBefore(';')
        .trim()
        .equals("text/markdown", ignoreCase = true)

@Composable
fun ImageBubble(model: String?, width: Int, height: Int, onClick: () -> Unit = {}) {
    val aspectRatio = if (height > 0) width.toFloat() / height.toFloat() else 1f
    val displayWidth = 200.dp
    AsyncImage(
        model = model,
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
fun FileBubble(name: String, size: Long, onClick: () -> Unit = {}) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
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
fun CardBubble(
    json: String,
    fallbackText: String = "",
    mediaController: ChatMediaController? = null,
    resolvedMediaUrls: Map<String, String> = emptyMap(),
    onMediaNeeded: (String) -> Unit = {},
    onAction: (String, String) -> Unit = { _, _ -> },
    onOpenMiniApp: (Int, String) -> Unit = { _, _ -> }
) {
    com.pinealctx.nexus.ui.components.AdaptiveCardView(
        json = json,
        fallbackText = fallbackText,
        mediaController = mediaController,
        resolvedMediaUrls = resolvedMediaUrls,
        onMediaNeeded = onMediaNeeded,
        onAction = onAction,
        onOpenMiniApp = onOpenMiniApp
    )
}

private val MessageContent.fileIdOrNull: String?
    get() = when (this) {
        is MessageContent.Image -> fileId
        is MessageContent.File -> fileId
        is MessageContent.Audio -> fileId
        is MessageContent.Video -> fileId
        else -> null
    }

private val MessageContent.fileIds: List<String>
    get() {
        val content = this
        return buildList {
            content.fileIdOrNull?.takeIf { it.isNotBlank() }?.let(::add)
            (content as? MessageContent.Video)
                ?.thumbnailFileId
                ?.takeIf { it.isNotBlank() }
                ?.let(::add)
        }
    }

@Composable
private fun groupEventText(event: MessageContent.GroupEvent, memberNames: Map<Int, String>): String {
    val resources = LocalResources.current
    fun name(userId: Int): String = memberNames[userId]
        ?: resources.getString(R.string.chat_user_fallback, userId)
    val members = event.memberIds.joinToString(", ") { name(it) }
    return when (event.type) {
        GroupEventType.MEMBER_JOINED -> event.inviterId?.let { inviterId ->
            stringResource(R.string.group_event_invited, name(inviterId), members)
        } ?: stringResource(R.string.group_event_joined, members)
        GroupEventType.MEMBER_LEFT -> stringResource(
            R.string.group_event_left,
            event.memberIds.firstOrNull()?.let(::name).orEmpty()
        )
        GroupEventType.MEMBER_REMOVED -> event.operatorId?.let { operatorId ->
            stringResource(R.string.group_event_removed_by, name(operatorId), members)
        } ?: stringResource(R.string.group_event_removed, members)
        GroupEventType.GROUP_INFO_CHANGED -> {
            val field = when (event.changedField) {
                "name" -> stringResource(R.string.group_event_field_name)
                "avatar" -> stringResource(R.string.group_event_field_avatar)
                "description" -> stringResource(R.string.group_event_field_description)
                else -> stringResource(R.string.group_event_field_information)
            }
            event.operatorId?.let { operatorId ->
                stringResource(R.string.group_event_changed_by, name(operatorId), field)
            } ?: stringResource(R.string.group_event_changed, field)
        }
        GroupEventType.UNKNOWN -> stringResource(R.string.message_group_update)
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

private fun Long.formatMessageTime(): String {
    if (this <= 0L) return ""
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(this))
}
