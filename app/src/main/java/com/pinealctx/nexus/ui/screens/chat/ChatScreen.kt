package com.pinealctx.nexus.ui.screens.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
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
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.pinealctx.nexus.R
import com.pinealctx.nexus.core.MessageData
import com.pinealctx.nexus.core.MessageSearchResultData
import com.pinealctx.nexus.ui.components.EmojiPicker
import com.pinealctx.nexus.ui.components.ImagePreviewDialog
import kotlinx.coroutines.flow.distinctUntilChanged
import uniffi.nexus_ffi.MessageContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: String,
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var inputText by remember { mutableStateOf(viewModel.initialDraft) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showMediaPicker by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<MessageSearchResultData>>(emptyList()) }
    var previewImageId by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val listState = rememberLazyListState()

    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = layoutInfo.totalItemsCount
            totalItems > 0 && lastVisibleIndex >= totalItems - 3
        }
            .distinctUntilChanged()
            .collect { shouldLoadMore ->
                if (shouldLoadMore) {
                    viewModel.loadMore()
                }
            }
    }

    if (previewImageId != null) {
        ImagePreviewDialog(
            model = previewImageId!!,
            onDismiss = { previewImageId = null }
        )
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.saveDraft(inputText) }
    }

    Scaffold(
        topBar = {
            if (showSearch) {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { query ->
                                searchQuery = query
                                viewModel.searchInConversation(query) { results ->
                                    searchResults = results
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(R.string.chat_search)) },
                            singleLine = true
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            showSearch = false
                            searchQuery = ""
                            searchResults = emptyList()
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.chat_close_search))
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.chat_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSearch = true }) {
                            Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search_title))
                        }
                    }
                )
            }
        },
        bottomBar = {
            Column {
                if (showEmojiPicker) {
                    EmojiPicker(
                        onEmojiSelected = { emoji ->
                            inputText += emoji
                        }
                    )
                }
                if (showMediaPicker) {
                    com.pinealctx.nexus.ui.components.MediaPickerBar(
                        onImageSelected = { uri ->
                            showMediaPicker = false
                            val resolver = context.contentResolver
                            val bytes = resolver.openInputStream(uri)?.readBytes() ?: return@MediaPickerBar
                            val fileName = uri.lastPathSegment ?: "image.jpg"
                            viewModel.sendImageMessage(bytes, fileName, 0, 0)
                        },
                        onFileSelected = { uri ->
                            showMediaPicker = false
                            val resolver = context.contentResolver
                            val bytes = resolver.openInputStream(uri)?.readBytes() ?: return@MediaPickerBar
                            val fileName = uri.lastPathSegment ?: "file"
                            val mimeType = resolver.getType(uri) ?: "application/octet-stream"
                            viewModel.sendFileMessage(bytes, fileName, mimeType)
                        },
                        onCameraCapture = { showMediaPicker = false }
                    )
                }
                Surface(tonalElevation = 2.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
                            onValueChange = { inputText = it },
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
                            onClick = {
                                viewModel.sendMessage(inputText)
                                inputText = ""
                                viewModel.saveDraft("")
                            },
                            enabled = inputText.isNotBlank() && !uiState.isSending
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.chat_send))
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                reverseLayout = true,
                state = listState
            ) {
                items(uiState.messages, key = { it.messageId }) { message ->
                    MessageBubble(
                        message = message,
                        onImageClick = { fileId -> previewImageId = fileId }
                    )
                }
            }

            // Search results overlay
            if (showSearch && searchResults.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                ) {
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
                                modifier = Modifier.clickable {
                                    showSearch = false
                                    searchQuery = ""
                                    searchResults = emptyList()
                                    // TODO: scroll to message by ID
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: MessageData, onImageClick: (String) -> Unit = {}) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "User ${message.senderId}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            MessageContentView(content = message.content, recalled = message.recalled, onImageClick = onImageClick)
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

private fun formatSearchTime(millis: Long): String {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(millis))
}
