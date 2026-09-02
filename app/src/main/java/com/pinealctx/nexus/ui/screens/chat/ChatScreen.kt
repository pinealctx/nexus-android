package com.pinealctx.nexus.ui.screens.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pinealctx.nexus.R
import com.pinealctx.nexus.core.MessageContent
import com.pinealctx.nexus.core.MessageSearchResultData
import com.pinealctx.nexus.ui.components.ImagePreviewDialog
import com.pinealctx.nexus.ui.components.NexusAvatar
import com.pinealctx.nexus.ui.components.NexusAvatarBadge
import com.pinealctx.nexus.ui.components.rememberChatMediaController
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
    onGroupDetails: (Int) -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var inputValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = viewModel.initialDraft,
                selection = TextRange(viewModel.initialDraft.length)
            )
        )
    }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<MessageSearchResultData>>(emptyList()) }
    var previewImageId by remember { mutableStateOf<String?>(null) }
    var replyTarget by remember { mutableStateOf<ChatMessageItem.Remote?>(null) }
    var editTarget by remember { mutableStateOf<ChatMessageItem.Remote?>(null) }
    var actionConfirmation by remember { mutableStateOf<MessageActionConfirmation?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val listState = rememberLazyListState()
    val mediaController = rememberChatMediaController()
    val snackbarHostState = remember { SnackbarHostState() }
    val latestDraftText by rememberUpdatedState(inputValue.text)

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

    LaunchedEffect(uiState.error, uiState.messages.isNotEmpty()) {
        val error = uiState.error ?: return@LaunchedEffect
        if (uiState.messages.isNotEmpty()) {
            snackbarHostState.showSnackbar(
                message = error,
                withDismissAction = true,
                duration = SnackbarDuration.Long
            )
            viewModel.clearError()
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
        onDispose { viewModel.saveDraft(latestDraftText) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    title = {
                        Row(
                            modifier = Modifier.clickable(
                                enabled = uiState.isGroupConversation && uiState.conversationPeerId > 0,
                                onClick = { onGroupDetails(uiState.conversationPeerId) }
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NexusAvatar(
                                id = uiState.conversationPeerId,
                                name = uiState.conversationTitle,
                                avatarUrl = uiState.conversationAvatarUrl,
                                size = 36.dp,
                                badge = if (uiState.isGroupConversation) NexusAvatarBadge.Group else null
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = uiState.conversationTitle.ifBlank {
                                    stringResource(R.string.chat_title)
                                },
                                maxLines = 1
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    actions = {
                        if (uiState.isGroupConversation && uiState.conversationPeerId > 0) {
                            IconButton(onClick = { onGroupDetails(uiState.conversationPeerId) }) {
                                Icon(Icons.Filled.Groups, contentDescription = stringResource(R.string.group_details))
                            }
                        }
                        IconButton(onClick = { showSearch = true }) {
                            Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search_title))
                        }
                    }
                )
            }
        },
        bottomBar = {
            ChatComposer(
                inputValue = inputValue,
                onInputChange = { inputValue = it },
                replyTarget = replyTarget,
                editTarget = editTarget,
                mediaUploadName = uiState.mediaUploadName,
                onClearReply = { replyTarget = null },
                onClearEdit = {
                    editTarget = null
                    inputValue = TextFieldValue("")
                },
                onSubmit = { text ->
                    val editing = editTarget
                    if (editing != null) {
                        viewModel.editMessage(editing.data.messageId, text)
                    } else {
                        viewModel.sendMessage(text, replyTarget?.data?.messageId)
                    }
                    inputValue = TextFieldValue("")
                    replyTarget = null
                    editTarget = null
                    viewModel.saveDraft("")
                },
                onSendVisualMedia = viewModel::sendVisualMedia,
                onSendFile = viewModel::sendFile,
                onSendVoiceRecording = viewModel::sendVoiceRecording
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading && uiState.messages.isEmpty() -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.error != null && uiState.messages.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.chat_load_failed),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = uiState.error.orEmpty(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = viewModel::retryLoad) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
                uiState.messages.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.chat_empty),
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        reverseLayout = true,
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.Bottom)
                    ) {
                        items(uiState.messages, key = { it.stableId }) { message ->
                            MessageBubble(
                                message = message,
                                currentUserId = uiState.currentUserId,
                                senderName = uiState.senderNames[message.senderId],
                                senderAvatarUrl = uiState.senderAvatarUrls[message.senderId],
                                groupMemberNames = uiState.senderNames,
                                showSenderName = uiState.isGroupConversation,
                                showSenderAvatar = uiState.isGroupConversation,
                                pendingActionMessageId = uiState.pendingMessageActionId,
                                onReply = {
                                    editTarget = null
                                    replyTarget = it
                                },
                                onEdit = { target ->
                                    replyTarget = null
                                    editTarget = target
                                    val editText = (target.content as MessageContent.Text).text
                                    inputValue = TextFieldValue(editText, TextRange(editText.length))
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
                                mediaUrls = uiState.mediaUrls,
                                mediaController = mediaController,
                                onMediaNeeded = viewModel::resolveMediaUrl,
                                onImageClick = { fileId ->
                                    uiState.mediaUrls[fileId]?.let { previewImageId = it }
                                },
                                onOpenMedia = { fileId ->
                                    uiState.mediaUrls[fileId]?.let { url ->
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    }
                                },
                                onCardAction = viewModel::submitCardAction,
                                onOpenMiniApp = { agentUserId, startParam ->
                                    if (agentUserId > 0) {
                                        val intent = Intent(
                                            context,
                                            com.pinealctx.nexus.ui.screens.miniapp.MiniAppActivity::class.java
                                        ).apply {
                                            putExtra(
                                                com.pinealctx.nexus.ui.screens.miniapp.MiniAppActivity.EXTRA_AGENT_USER_ID,
                                                agentUserId
                                            )
                                            putExtra(
                                                com.pinealctx.nexus.ui.screens.miniapp.MiniAppActivity.EXTRA_CONVERSATION_ID,
                                                conversationId.toLongOrNull() ?: 0L
                                            )
                                            putExtra(
                                                com.pinealctx.nexus.ui.screens.miniapp.MiniAppActivity.EXTRA_START_PARAM,
                                                startParam
                                            )
                                        }
                                        context.startActivity(intent)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            if (uiState.isLocatingMessage || uiState.isLoadingMore) {
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
