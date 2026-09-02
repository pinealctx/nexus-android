package com.pinealctx.nexus.ui.screens.chat

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.pinealctx.nexus.R
import com.pinealctx.nexus.ui.components.EmojiPicker
import com.pinealctx.nexus.ui.components.MediaPickerBar

@Composable
fun ChatComposer(
    inputValue: TextFieldValue,
    onInputChange: (TextFieldValue) -> Unit,
    replyTarget: ChatMessageItem.Remote?,
    editTarget: ChatMessageItem.Remote?,
    mediaUploadName: String?,
    onClearReply: () -> Unit,
    onClearEdit: () -> Unit,
    onSubmit: (String) -> Unit,
    onSendVisualMedia: (Uri) -> Unit,
    onSendFile: (Uri) -> Unit,
    onSendVoiceRecording: (VoiceRecording) -> Unit
) {
    val context = LocalContext.current
    val voicePermissionDenied = stringResource(R.string.voice_permission_denied)
    val voiceRecorder = remember(context.applicationContext) { VoiceRecorder(context.applicationContext) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showMediaPicker by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingStartedAt by remember { mutableLongStateOf(0L) }
    var recordingElapsedMs by remember { mutableLongStateOf(0L) }
    var recordingError by remember { mutableStateOf<String?>(null) }

    fun startRecording() {
        runCatching { voiceRecorder.start() }
            .onSuccess {
                recordingStartedAt = SystemClock.elapsedRealtime()
                recordingElapsedMs = 0L
                isRecording = true
                recordingError = null
                showEmojiPicker = false
                showMediaPicker = false
            }
            .onFailure { recordingError = it.message }
    }

    val recordPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecording() else recordingError = voicePermissionDenied
    }

    LaunchedEffect(isRecording) {
        while (isRecording) {
            recordingElapsedMs = SystemClock.elapsedRealtime() - recordingStartedAt
            kotlinx.coroutines.delay(100L)
        }
    }

    DisposableEffect(voiceRecorder) {
        onDispose(voiceRecorder::cancel)
    }

    Column {
        if (showEmojiPicker) {
            EmojiPicker(
                onEmojiSelected = { emoji ->
                    onInputChange(insertTextAtSelection(inputValue, emoji))
                }
            )
        }
        if (showMediaPicker) {
            MediaPickerBar(
                onImageSelected = { uri ->
                    showMediaPicker = false
                    onSendVisualMedia(uri)
                },
                onFileSelected = { uri ->
                    showMediaPicker = false
                    onSendFile(uri)
                }
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
                mediaUploadName?.let { fileName ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.media_uploading, fileName),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
                recordingError?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp)
                    )
                }
                if (isRecording) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            voiceRecorder.cancel()
                            isRecording = false
                            recordingElapsedMs = 0L
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.voice_cancel))
                        }
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(Color(0xFFE53935), RoundedCornerShape(50))
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = formatRecordingDuration(recordingElapsedMs),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            runCatching { voiceRecorder.stop() }
                                .onSuccess { recording ->
                                    isRecording = false
                                    recordingElapsedMs = 0L
                                    onSendVoiceRecording(recording)
                                }
                                .onFailure {
                                    isRecording = false
                                    recordingElapsedMs = 0L
                                    recordingError = it.message
                                }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.voice_send))
                        }
                    }
                } else Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showEmojiPicker = !showEmojiPicker; showMediaPicker = false }) {
                        Icon(
                            Icons.Filled.EmojiEmotions,
                            contentDescription = stringResource(R.string.chat_emoji),
                            tint = if (showEmojiPicker) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedTextField(
                        value = inputValue,
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
                    if (inputValue.text.isBlank() && editTarget == null) {
                        IconButton(onClick = {
                            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                                PackageManager.PERMISSION_GRANTED
                            ) {
                                startRecording()
                            } else {
                                recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }) {
                            Icon(Icons.Filled.Mic, contentDescription = stringResource(R.string.voice_record))
                        }
                    }
                    IconButton(
                        onClick = { onSubmit(inputValue.text) },
                        enabled = inputValue.text.isNotBlank()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.chat_send))
                    }
                }
            }
        }
    }
}

private fun formatRecordingDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1000L
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}

internal fun insertTextAtSelection(value: TextFieldValue, insertedText: String): TextFieldValue {
    val start = minOf(value.selection.start, value.selection.end)
    val end = maxOf(value.selection.start, value.selection.end)
    val updatedText = value.text.replaceRange(start, end, insertedText)
    val cursor = start + insertedText.length
    return value.copy(text = updatedText, selection = TextRange(cursor))
}
