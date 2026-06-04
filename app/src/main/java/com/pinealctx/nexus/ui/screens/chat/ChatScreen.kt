package com.pinealctx.nexus.ui.screens.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.pinealctx.nexus.R
import com.pinealctx.nexus.core.MessageContent
import com.pinealctx.nexus.core.MessageSearchResultData
import com.pinealctx.nexus.ui.components.ImagePreviewDialog
import kotlinx.coroutines.flow.distinctUntilChanged

private enum class MessageActionKind {
    RECALL,
    DELETE
}

private data class MessageActionConfirmation(
    val kind: MessageActionKind,
    val message: ChatMessageItem.Remote
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: String,
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var inputText by remember { mutableStateOf(viewModel.initialDraft) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<MessageSearchResultData>>(emptyList()) }
    var previewImageId by remember { mutableStateOf<String?>(null) }
    var replyTarget by remember { mutableStateOf<ChatMessageItem.Remote?>(null) }
    var editTarget by remember { mutableStateOf<ChatMessageItem.Remote?>(null) }
    var actionConfirmation by remember { mutableStateOf<MessageActionConfirmation?>(null) }
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

    LaunchedEffect(uiState.scrollToMessageId, uiState.messages) {
        val messageId = uiState.scrollToMessageId ?: return@LaunchedEffect
        val index = ChatMessageIndex.remoteIndexOf(uiState.messages, messageId)
        if (index >= 0) {
            listState.animateScrollToItem(index)
            viewModel.consumeScrollTarget(messageId)
        }
    }

    if (previewImageId != null) {
        ImagePreviewDialog(
            model = previewImageId!!,
            onDismiss = { previewImageId = null }
        )
    }

    actionConfirmation?.let { confirmation ->
        MessageActionConfirmDialog(
            confirmation = confirmation,
            onDismiss = { actionConfirmation = null },
            onConfirm = {
                actionConfirmation = null
                when (confirmation.kind) {
                    MessageActionKind.RECALL -> viewModel.recallMessage(confirmation.message.data.messageId)
                    MessageActionKind.DELETE -> viewModel.deleteMessage(confirmation.message.data.messageId)
                }
            }
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
            ChatComposer(
                inputText = inputText,
                onInputChange = { inputText = it },
                replyTarget = replyTarget,
                editTarget = editTarget,
                onClearReply = { replyTarget = null },
                onClearEdit = {
                    editTarget = null
                    inputText = ""
                },
                onSubmit = { text ->
                    val editing = editTarget
                    if (editing != null) {
                        viewModel.editMessage(editing.data.messageId, text)
                    } else {
                        viewModel.sendMessage(text, replyTarget?.data?.messageId)
                    }
                    inputText = ""
                    replyTarget = null
                    editTarget = null
                    viewModel.saveDraft("")
                },
                onSendImageMessage = viewModel::sendImageMessage,
                onSendFileMessage = viewModel::sendFileMessage
            )
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
                items(uiState.messages, key = { it.stableId }) { message ->
                    MessageBubble(
                        message = message,
                        currentUserId = uiState.currentUserId,
                        pendingActionMessageId = uiState.pendingMessageActionId,
                        onReply = {
                            editTarget = null
                            replyTarget = it
                        },
                        onEdit = { target ->
                            replyTarget = null
                            editTarget = target
                            inputText = (target.content as MessageContent.Text).text
                        },
                        onRecall = { target ->
                            actionConfirmation = MessageActionConfirmation(MessageActionKind.RECALL, target)
                        },
                        onDelete = { target ->
                            actionConfirmation = MessageActionConfirmation(MessageActionKind.DELETE, target)
                        },
                        onCopy = { text ->
                            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboardManager.setPrimaryClip(ClipData.newPlainText("message", text))
                        },
                        onRetry = { clientMessageId -> viewModel.retryMessage(clientMessageId) },
                        onImageClick = { fileId -> previewImageId = fileId }
                    )
                }
            }

            if (uiState.isLocatingMessage) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                )
            }

            if (showSearch && searchResults.isNotEmpty()) {
                ChatSearchOverlay(
                    searchResults = searchResults,
                    isLocatingMessage = uiState.isLocatingMessage,
                    onResultClick = { result ->
                        showSearch = false
                        searchQuery = ""
                        searchResults = emptyList()
                        viewModel.revealMessage(result.messageId)
                    }
                )
            }
        }
    }
}

@Composable
private fun MessageActionConfirmDialog(
    confirmation: MessageActionConfirmation,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val isRecall = confirmation.kind == MessageActionKind.RECALL
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (isRecall) R.string.chat_recall_confirm_title else R.string.chat_delete_confirm_title
                )
            )
        },
        text = {
            Text(
                stringResource(
                    if (isRecall) R.string.chat_recall_confirm_message else R.string.chat_delete_confirm_message
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(
                        if (isRecall) R.string.chat_action_recall else R.string.chat_action_delete
                    ),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
