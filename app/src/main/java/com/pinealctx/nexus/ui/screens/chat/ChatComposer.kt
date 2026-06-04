package com.pinealctx.nexus.ui.screens.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pinealctx.nexus.R
import com.pinealctx.nexus.ui.components.EmojiPicker
import com.pinealctx.nexus.ui.components.MediaPickerBar

@Composable
fun ChatComposer(
    inputText: String,
    onInputChange: (String) -> Unit,
    replyTarget: ChatMessageItem.Remote?,
    editTarget: ChatMessageItem.Remote?,
    onClearReply: () -> Unit,
    onClearEdit: () -> Unit,
    onSubmit: (String) -> Unit,
    onSendImageMessage: (ByteArray, String, Int, Int) -> Unit,
    onSendFileMessage: (ByteArray, String, String) -> Unit
) {
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showMediaPicker by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column {
        if (showEmojiPicker) {
            EmojiPicker(
                onEmojiSelected = { emoji ->
                    onInputChange(inputText + emoji)
                }
            )
        }
        if (showMediaPicker) {
            MediaPickerBar(
                onImageSelected = { uri ->
                    showMediaPicker = false
                    val resolver = context.contentResolver
                    val bytes = resolver.openInputStream(uri)?.readBytes() ?: return@MediaPickerBar
                    val fileName = uri.lastPathSegment ?: "image.jpg"
                    onSendImageMessage(bytes, fileName, 0, 0)
                },
                onFileSelected = { uri ->
                    showMediaPicker = false
                    val resolver = context.contentResolver
                    val bytes = resolver.openInputStream(uri)?.readBytes() ?: return@MediaPickerBar
                    val fileName = uri.lastPathSegment ?: "file"
                    val mimeType = resolver.getType(uri) ?: "application/octet-stream"
                    onSendFileMessage(bytes, fileName, mimeType)
                },
                onCameraCapture = { showMediaPicker = false }
            )
        }
        Surface(tonalElevation = 2.dp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                replyTarget?.let { target ->
                    ReplyComposerPreview(
                        message = target,
                        onClear = onClearReply
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
                editTarget?.let { target ->
                    EditComposerPreview(
                        message = target,
                        onClear = onClearEdit
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showEmojiPicker = !showEmojiPicker; showMediaPicker = false }) {
                        Icon(
                            Icons.Filled.EmojiEmotions,
                            contentDescription = stringResource(R.string.chat_emoji),
                            tint = if (showEmojiPicker) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = onInputChange,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.chat_placeholder)) },
                        maxLines = 4,
                        shape = RoundedCornerShape(24.dp)
                    )
                    IconButton(onClick = { showMediaPicker = !showMediaPicker; showEmojiPicker = false }) {
                        Icon(
                            Icons.Filled.Image,
                            contentDescription = null,
                            tint = if (showMediaPicker) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { onSubmit(inputText) },
                        enabled = inputText.isNotBlank()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.chat_send))
                    }
                }
            }
        }
    }
}
