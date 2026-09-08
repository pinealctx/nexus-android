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
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
    onSendVoiceRecording: (VoiceRecording) -> Unit,
    inputEntities: List<com.pinealctx.nexus.core.TextEntityData> = emptyList(),
    focusRequestKey: Int = 0
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
    val inputFocusRequester = remember { FocusRequester() }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(focusRequestKey) {
        if (focusRequestKey > 0 && !isRecording) {
            inputFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    val mentionColor = MaterialTheme.colorScheme.primary

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
                    inputFocusRequester.requestFocus()
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
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 7.dp)
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
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 3.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f),
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(9.dp))
                            Text(
                                text = stringResource(R.string.media_uploading, fileName),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1
                            )
                        }
                    }
                }
                recordingError?.let { error ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 3.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
                if (isRecording) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalIconButton(
                            onClick = {
                                voiceRecorder.cancel()
                                isRecording = false
                                recordingElapsedMs = 0L
                            },
                            modifier = Modifier.size(46.dp)
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.voice_cancel)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(MaterialTheme.colorScheme.error, RoundedCornerShape(50))
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.voice_recording),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatRecordingDuration(recordingElapsedMs),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        FilledIconButton(
                            onClick = {
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
                            },
                            modifier = Modifier.size(46.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = stringResource(R.string.voice_send)
                            )
                        }
                    }
                } else Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            showEmojiPicker = !showEmojiPicker
                            showMediaPicker = false
                        },
                        modifier = Modifier.size(46.dp)
                    ) {
                        Icon(
                            Icons.Filled.EmojiEmotions,
                            contentDescription = stringResource(R.string.chat_emoji),
                            tint = if (showEmojiPicker) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(7.dp))
                    TextField(
                        value = inputValue,
                        onValueChange = onInputChange,
                        visualTransformation = androidx.compose.ui.text.input.VisualTransformation { text ->
                            val styled = androidx.compose.ui.text.buildAnnotatedString {
                                append(text)
                                com.pinealctx.nexus.core.validTextEntities(text.text, inputEntities)
                                    .filter { it.type == com.shared.v1.MessageEntityType.MESSAGE_ENTITY_TYPE_MENTION }
                                    .forEach {
                                        addStyle(
                                            androidx.compose.ui.text.SpanStyle(color = mentionColor),
                                            it.offset, it.offset + it.length
                                        )
                                    }
                            }
                            androidx.compose.ui.text.input.TransformedText(styled, androidx.compose.ui.text.input.OffsetMapping.Identity)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 50.dp, max = 128.dp)
                            .focusRequester(inputFocusRequester),
                        placeholder = { Text(stringResource(R.string.chat_placeholder)) },
                        maxLines = 4,
                        shape = RoundedCornerShape(22.dp),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    showMediaPicker = !showMediaPicker
                                    showEmojiPicker = false
                                },
                                enabled = mediaUploadName == null
                            ) {
                                Icon(
                                    Icons.Filled.AddCircle,
                                    contentDescription = stringResource(R.string.chat_add_attachment),
                                    tint = if (showMediaPicker) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
                            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                        )
                    )
                    Spacer(Modifier.width(7.dp))
                    if (inputValue.text.isNotBlank() || editTarget != null) {
                        FilledIconButton(
                            onClick = {
                                onSubmit(inputValue.text)
                                showEmojiPicker = false
                                showMediaPicker = false
                            },
                            enabled = inputValue.text.isNotBlank(),
                            modifier = Modifier.size(46.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = stringResource(R.string.chat_send)
                            )
                        }
                    } else {
                        FilledTonalIconButton(
                            onClick = {
                                if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                                    PackageManager.PERMISSION_GRANTED
                                ) {
                                    startRecording()
                                } else {
                                    recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            enabled = mediaUploadName == null,
                            modifier = Modifier.size(46.dp)
                        ) {
                            Icon(
                                Icons.Filled.Mic,
                                contentDescription = stringResource(R.string.voice_record)
                            )
                        }
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
