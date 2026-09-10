package com.pinealctx.nexus.ui.screens.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pinealctx.nexus.R
import com.pinealctx.nexus.core.MessageContent
import com.pinealctx.nexus.ui.components.ImagePreviewDialog
import com.pinealctx.nexus.ui.components.NexusAvatar
import com.pinealctx.nexus.ui.components.NexusAvatarBadge
import com.pinealctx.nexus.ui.components.rememberChatMediaController
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

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
    initialMessageId: Long = 0,
    onGroupDetails: (Int) -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel(),
    reactionsModel: ReactionsViewModel = hiltViewModel()
) {
    val reactionsState by reactionsModel.state.collectAsState()
    ReactionSheets(reactionsState, reactionsModel)
    val uiState by viewModel.uiState.collectAsState()
    var initialMessageHandled by remember(conversationId, initialMessageId) { mutableStateOf(false) }
    LaunchedEffect(initialMessageId, uiState.messages.isNotEmpty()) {
        if (!initialMessageHandled && initialMessageId > 0 && uiState.messages.any { it is ChatMessageItem.Remote }) {
            initialMessageHandled = true
            viewModel.revealMessage(initialMessageId)
        }
    }
    val searchState by viewModel.searchState.collectAsState()
    var inputEntities by remember { mutableStateOf(viewModel.initialDraftEntities) }
    var inputValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = viewModel.initialDraft,
                selection = TextRange(viewModel.initialDraft.length)
            )
        )
    }
    var showSearch by remember { mutableStateOf(false) }
    var showAgentTools by remember { mutableStateOf(false) }
    var composerFocusRequest by remember { mutableIntStateOf(0) }
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(showSearch) {
        if (showSearch) searchFocusRequester.requestFocus()
    }
    var highlightedMessageId by remember { mutableStateOf<Long?>(null) }
    var previewImageId by remember { mutableStateOf<String?>(null) }
    var replyTarget by remember { mutableStateOf<ChatMessageItem.Remote?>(null) }
    var editTarget by remember { mutableStateOf<ChatMessageItem.Remote?>(null) }
    var draftBeforeEdit by remember { mutableStateOf<Pair<TextFieldValue, List<com.pinealctx.nexus.core.TextEntityData>>?>(null) }
    var actionConfirmation by remember { mutableStateOf<MessageActionConfirmation?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val openMiniApp: (Int, String) -> Unit = { agentUserId, startParam ->
        if (agentUserId > 0) {
            context.startActivity(Intent(context, com.pinealctx.nexus.ui.screens.miniapp.MiniAppActivity::class.java).apply {
                putExtra(com.pinealctx.nexus.ui.screens.miniapp.MiniAppActivity.EXTRA_AGENT_USER_ID, agentUserId)
                putExtra(com.pinealctx.nexus.ui.screens.miniapp.MiniAppActivity.EXTRA_CONVERSATION_ID, conversationId.toLongOrNull() ?: 0L)
                putExtra(com.pinealctx.nexus.ui.screens.miniapp.MiniAppActivity.EXTRA_START_PARAM, startParam)
            })
        }
    }
    val selectCommand: (com.pinealctx.nexus.core.AgentCommandData) -> Unit = { command ->
        val updated = insertAgentCommand(inputValue, command.command)
        inputEntities = com.pinealctx.nexus.core.adjustTextEntities(inputValue.text, updated.text, inputEntities, 0)
        inputValue = updated
        showAgentTools = false
        composerFocusRequest++
    }
    BackHandler(enabled = showSearch) {
        showSearch = false
        viewModel.closeSearch()
    }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val timelineItems = remember(uiState.messages) {
        buildChatTimeline(uiState.messages, ZoneId.systemDefault())
    }
    val showScrollToLatest by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 1 || listState.firstVisibleItemScrollOffset > 160
        }
    }
    val mediaController = rememberChatMediaController()
    val snackbarHostState = remember { SnackbarHostState() }
    val latestDraftText by rememberUpdatedState(inputValue.text)
    val latestDraftEntities by rememberUpdatedState(inputEntities)
    val latestEditTarget by rememberUpdatedState(editTarget)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP && latestEditTarget == null) {
                viewModel.saveDraft(latestDraftText, latestDraftEntities)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(inputValue.text, inputEntities, editTarget) {
        if (editTarget == null) {
            kotlinx.coroutines.delay(300)
            viewModel.saveDraft(inputValue.text, inputEntities)
        }
    }
    val activeMentionQuery = if (uiState.isGroupConversation) mentionQuery(inputValue) else null
    LaunchedEffect(activeMentionQuery?.start) {
        if (activeMentionQuery != null) viewModel.loadMentionCandidates()
    }
    val newestOutgoingLocalMessageId = remember(uiState.messages, uiState.currentUserId) {
        findNewestOutgoingLocalMessageId(uiState.messages, uiState.currentUserId)
    }
    var lastAutoScrolledLocalMessageId by remember(conversationId) { mutableStateOf<Long?>(null) }

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

    LaunchedEffect(uiState.scrollToMessageId, timelineItems) {
        val messageId = uiState.scrollToMessageId ?: return@LaunchedEffect
        val index = timelineItems.indexOfFirst { item ->
            val message = (item as? ChatTimelineItem.Message)?.message
            message is ChatMessageItem.Remote && message.data.messageId == messageId
        }
        if (index >= 0) {
            listState.animateScrollToItem(index)
            highlightedMessageId = messageId
            viewModel.consumeScrollTarget(messageId)
        }
    }

    LaunchedEffect(highlightedMessageId) {
        if (highlightedMessageId != null) {
            kotlinx.coroutines.delay(1800)
            highlightedMessageId = null
        }
    }

    LaunchedEffect(newestOutgoingLocalMessageId) {
        val clientMessageId = newestOutgoingLocalMessageId ?: return@LaunchedEffect
        if (clientMessageId == lastAutoScrolledLocalMessageId) return@LaunchedEffect
        lastAutoScrolledLocalMessageId = clientMessageId
        withFrameNanos { }
        listState.animateScrollToItem(0)
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
        onDispose { if (latestEditTarget == null) viewModel.saveDraft(latestDraftText, latestDraftEntities) }
    }

    uiState.mentionedUserId?.let { userId ->
        ModalBottomSheet(onDismissRequest = viewModel::dismissMentionedUser) {
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                val user = uiState.mentionedUser
                if (uiState.isLoadingMentionedUser) {
                    CircularProgressIndicator()
                } else if (user == null) {
                    TextButton(onClick = { viewModel.showMentionedUser(userId) }) {
                        Text(stringResource(R.string.chat_mention_profile_retry))
                    }
                } else {
                    com.pinealctx.nexus.ui.components.NexusAvatar(
                        id = user.userId,
                        name = user.nickname.ifBlank { user.username },
                        avatarUrl = user.avatarUrl,
                        size = 64.dp
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(user.nickname.ifBlank { user.username }, style = MaterialTheme.typography.headlineSmall)
                    if (user.username.isNotBlank()) Text("@${user.username}", style = MaterialTheme.typography.bodyLarge)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showAgentTools) {
        ModalBottomSheet(onDismissRequest = { showAgentTools = false }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Text(stringResource(R.string.chat_agent_tools), Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.chat_commands_hint), Modifier.padding(24.dp), style = MaterialTheme.typography.bodySmall)
                if (uiState.isLoadingAgentTools) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (uiState.agentToolsLoadFailed) {
                    TextButton(onClick = viewModel::loadAgentTools) { Text(stringResource(R.string.chat_agent_tools_retry)) }
                }
                uiState.agentInfo?.let { agent ->
                    if (agent.miniAppEnabled) {
                        FilledTonalButton(onClick = {
                            showAgentTools = false
                            openMiniApp(agent.userId, "")
                        }, modifier = Modifier.padding(horizontal = 24.dp)) {
                            Text(stringResource(R.string.chat_open_mini_app))
                        }
                    }
                    AgentCommandList(agent.commands, "", selectCommand)
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                if (showSearch) {
                    TopAppBar(
                        title = {
                            TextField(
                                value = searchState.query,
                                onValueChange = viewModel::setSearchQuery,
                                modifier = Modifier.fillMaxWidth().focusRequester(searchFocusRequester),
                                placeholder = { Text(stringResource(R.string.chat_search)) },
                                singleLine = true,
                                shape = RoundedCornerShape(18.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                                )
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = {
                                showSearch = false
                                viewModel.closeSearch()
                            }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.chat_close_search)
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
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
                                    size = 38.dp,
                                    badge = if (uiState.isGroupConversation) NexusAvatarBadge.Group else null
                                )
                                Spacer(Modifier.width(11.dp))
                                Column {
                                    Text(
                                        text = uiState.conversationTitle.ifBlank {
                                            stringResource(R.string.chat_title)
                                        },
                                        maxLines = 1,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (uiState.isGroupConversation) {
                                        Text(
                                            text = stringResource(R.string.chat_group_details_hint),
                                            maxLines = 1,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.back)
                                )
                            }
                        },
                        actions = {
                            if (uiState.agentInfo != null && editTarget == null) {
                                IconButton(onClick = { showAgentTools = true; viewModel.loadAgentTools() }) {
                                    Icon(Icons.Filled.SmartToy, stringResource(R.string.chat_agent_tools))
                                }
                            } else if (uiState.agentToolsLoadFailed) {
                                IconButton(onClick = viewModel::loadAgentTools) {
                                    Icon(Icons.Filled.Refresh, stringResource(R.string.chat_agent_tools_retry))
                                }
                            }
                            if (uiState.isGroupConversation && uiState.conversationPeerId > 0) {
                                IconButton(onClick = { onGroupDetails(uiState.conversationPeerId) }) {
                                    Icon(
                                        Icons.Filled.Groups,
                                        contentDescription = stringResource(R.string.group_details)
                                    )
                                }
                            }
                            IconButton(onClick = { showSearch = true }) {
                                Icon(
                                    Icons.Filled.Search,
                                    contentDescription = stringResource(R.string.search_title)
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
            }
        },
        bottomBar = {
            if (!showSearch) {
                Column {
                    if (editTarget == null && uiState.agentInfo != null) {
                        commandQuery(inputValue)?.let { query ->
                            AgentCommandList(uiState.agentInfo!!.commands, query, selectCommand)
                        }
                    }
                    activeMentionQuery?.let { query ->
                        MentionSuggestions(
                            candidates = uiState.mentionCandidates,
                            query = query,
                            loading = uiState.isLoadingMentions,
                            failed = uiState.mentionsLoadFailed,
                            onRetry = viewModel::loadMentionCandidates,
                            onSelect = { candidate ->
                                val (value, entities) = insertMention(inputValue, inputEntities, candidate)
                                inputValue = value
                                inputEntities = entities
                            }
                        )
                    }
                    ChatComposer(
                        focusRequestKey = composerFocusRequest,
                        inputValue = inputValue,
                        inputEntities = inputEntities,
                        onInputChange = {
                            inputEntities = com.pinealctx.nexus.core.adjustTextEntities(
                                inputValue.text, it.text, inputEntities, inputValue.selection.min
                            )
                            inputValue = it
                        },
                        replyTarget = replyTarget,
                        editTarget = editTarget,
                        mediaUploadName = uiState.mediaUploadName,
                        onClearReply = { replyTarget = null },
                        onClearEdit = {
                            editTarget = null
                            inputValue = draftBeforeEdit?.first ?: TextFieldValue("")
                            inputEntities = draftBeforeEdit?.second.orEmpty()
                            draftBeforeEdit = null
                        },
                        onSubmit = { text ->
                            val editing = editTarget
                            if (editing != null) {
                                viewModel.editMessage(editing.data.messageId, text, inputEntities)
                            } else {
                                viewModel.sendMessage(text, replyTarget?.data?.messageId, inputEntities)
                            }
                            inputValue = if (editing != null) draftBeforeEdit?.first ?: TextFieldValue("") else TextFieldValue("")
                            inputEntities = if (editing != null) draftBeforeEdit?.second.orEmpty() else emptyList()
                            draftBeforeEdit = null
                            replyTarget = null
                            editTarget = null
                            viewModel.saveDraft(inputValue.text, inputEntities)
                        },
                        onSendVisualMedia = viewModel::sendVisualMedia,
                        onSendFile = viewModel::sendFile,
                        onSendVoiceRecording = viewModel::sendVoiceRecording
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.16f))
        ) {
            when {
                uiState.isLoading && uiState.messages.isEmpty() -> {
                    ChatLoadingSkeleton(modifier = Modifier.fillMaxSize())
                }
                uiState.error != null && uiState.messages.isEmpty() -> {
                    ChatStateMessage(
                        modifier = Modifier.fillMaxSize(),
                        icon = {
                            Icon(
                                Icons.Outlined.CloudOff,
                                contentDescription = null,
                                modifier = Modifier.size(30.dp)
                            )
                        },
                        title = stringResource(R.string.chat_load_failed),
                        message = stringResource(R.string.chat_load_failed_desc),
                        actionLabel = stringResource(R.string.retry),
                        onAction = viewModel::retryLoad
                    )
                }
                uiState.messages.isEmpty() -> {
                    ChatStateMessage(
                        modifier = Modifier.fillMaxSize(),
                        icon = {
                            Icon(
                                Icons.AutoMirrored.Filled.Chat,
                                contentDescription = null,
                                modifier = Modifier.size(30.dp)
                            )
                        },
                        title = stringResource(R.string.chat_empty),
                        message = stringResource(R.string.chat_empty_desc)
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        reverseLayout = true,
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.Bottom)
                    ) {
                        items(timelineItems, key = { it.stableId }) { item ->
                            when (item) {
                                is ChatTimelineItem.DaySeparator -> ChatDaySeparator(item.timestamp)
                                is ChatTimelineItem.Message -> {
                                    val message = item.message
                                    Box(Modifier.fillMaxWidth().background(
                                        if ((message as? ChatMessageItem.Remote)?.data?.messageId == highlightedMessageId && highlightedMessageId != null)
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                        else androidx.compose.ui.graphics.Color.Transparent
                                    )) {
                                        MessageBubble(
                                            reactionEnabled = reactionsState.config.enabled,
                                            reactionOperation = (message as? ChatMessageItem.Remote)?.let { reactionsState.pending[it.data.messageId] },
                                            onReactionPicker = { (message as? ChatMessageItem.Remote)?.let { reactionsModel.picker(it.data) } },
                                            onReaction = { emoji -> (message as? ChatMessageItem.Remote)?.let { reactionsModel.toggle(it.data, emoji) } },
                                            onReactionDetails = { emoji -> (message as? ChatMessageItem.Remote)?.let { reactionsModel.details(it.data, emoji) } },
                                            onMentionClick = viewModel::showMentionedUser,
                                            message = message,
                                            currentUserId = uiState.currentUserId,
                                            senderName = uiState.senderNames[message.senderId],
                                            senderAvatarUrl = uiState.senderAvatarUrls[message.senderId],
                                            groupMemberNames = uiState.senderNames,
                                            showSenderName = uiState.isGroupConversation && item.showSenderIdentity,
                                            showSenderAvatar = uiState.isGroupConversation && item.showSenderIdentity,
                                            reserveSenderAvatarSpace = uiState.isGroupConversation,
                                            pendingActionMessageId = uiState.pendingMessageActionId,
                                            onReply = {
                                                if (editTarget != null) {
                                                    inputValue = draftBeforeEdit?.first ?: TextFieldValue("")
                                                    inputEntities = draftBeforeEdit?.second.orEmpty()
                                                    draftBeforeEdit = null
                                                }
                                                editTarget = null
                                                replyTarget = it
                                            },
                                            onEdit = { target ->
                                                if (editTarget == null) {
                                                    draftBeforeEdit = inputValue to inputEntities
                                                    viewModel.saveDraft(inputValue.text, inputEntities)
                                                }
                                                replyTarget = null
                                                editTarget = target
                                                val editText = (target.content as MessageContent.Text).text
                                                inputEntities = target.content.entities
                                                inputValue = TextFieldValue(editText, TextRange(editText.length))
                                            },
                                            onRecall = { target ->
                                                actionConfirmation = MessageActionConfirmation(
                                                    MessageActionKind.RECALL,
                                                    target
                                                )
                                            },
                                            onDelete = { target ->
                                                actionConfirmation = MessageActionConfirmation(
                                                    MessageActionKind.DELETE,
                                                    target
                                                )
                                            },
                                            onCopy = { text ->
                                                val clipboardManager = context.getSystemService(
                                                    Context.CLIPBOARD_SERVICE
                                                ) as ClipboardManager
                                                clipboardManager.setPrimaryClip(
                                                    ClipData.newPlainText("message", text)
                                                )
                                            },
                                            onRetry = viewModel::retryMessage,
                                            mediaUrls = uiState.mediaUrls,
                                            mediaController = mediaController,
                                            onMediaNeeded = viewModel::resolveMediaUrl,
                                            onImageClick = { fileId ->
                                                uiState.mediaUrls[fileId]?.let { previewImageId = it }
                                            },
                                            onOpenMedia = { fileId ->
                                                uiState.mediaUrls[fileId]?.let { url ->
                                                    context.startActivity(
                                                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                    )
                                                }
                                            },
                                            onCardAction = viewModel::submitCardAction,
                                            onOpenMiniApp = openMiniApp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (uiState.isRefreshing || uiState.isLocatingMessage || uiState.isLoadingMore) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                )
            }

            AnimatedVisibility(
                visible = showScrollToLatest && uiState.messages.isNotEmpty(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        coroutineScope.launch { listState.animateScrollToItem(0) }
                    },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.chat_scroll_to_latest)
                    )
                }
            }

            if (showSearch) {
                ChatSearchOverlay(
                    state = searchState,
                    onLoadMore = viewModel::loadMoreSearchResults,
                    onRetry = viewModel::retrySearch,
                    onResultClick = { result ->
                        keyboardController?.hide()
                        showSearch = false
                        viewModel.closeSearch()
                        viewModel.revealMessage(result.messageId)
                    }
                )
            }
        }
    }
}

internal sealed interface ChatTimelineItem {
    val stableId: String

    data class Message(
        val message: ChatMessageItem,
        val showSenderIdentity: Boolean
    ) : ChatTimelineItem {
        override val stableId: String = message.stableId
    }

    data class DaySeparator(
        val timestamp: Long,
        val epochDay: Long
    ) : ChatTimelineItem {
        override val stableId: String = "day:$epochDay"
    }
}

internal fun buildChatTimeline(
    messages: List<ChatMessageItem>,
    zoneId: ZoneId
): List<ChatTimelineItem> = buildList {
    messages.forEachIndexed { index, message ->
        val olderMessage = messages.getOrNull(index + 1)
        val messageDay = Instant.ofEpochMilli(message.createdAt).atZone(zoneId).toLocalDate()
        val olderDay = olderMessage?.let {
            Instant.ofEpochMilli(it.createdAt).atZone(zoneId).toLocalDate()
        }
        val showSenderIdentity = message.senderId > 0 && (
            olderMessage == null ||
                olderMessage.senderId != message.senderId ||
                messageDay != olderDay ||
                kotlin.math.abs(message.createdAt - olderMessage.createdAt) > MESSAGE_GROUP_WINDOW_MS
            )
        add(ChatTimelineItem.Message(message, showSenderIdentity))

        if (olderDay == null || messageDay != olderDay) {
            add(ChatTimelineItem.DaySeparator(message.createdAt, messageDay.toEpochDay()))
        }
    }
}

internal fun findNewestOutgoingLocalMessageId(
    messages: List<ChatMessageItem>,
    currentUserId: Int
): Long? = messages
    .asSequence()
    .filterIsInstance<ChatMessageItem.Local>()
    .filter { currentUserId <= 0 || it.senderId == currentUserId }
    .maxOfOrNull { it.data.clientMessageId }

@Composable
internal fun ChatDaySeparator(timestamp: Long) {
    val locale = LocalConfiguration.current.locales[0]
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            Text(
                text = formatChatDay(
                    timestamp = timestamp,
                    now = System.currentTimeMillis(),
                    zoneId = ZoneId.systemDefault(),
                    locale = locale,
                    todayLabel = stringResource(R.string.chat_today),
                    yesterdayLabel = stringResource(R.string.chat_yesterday)
                ),
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

internal fun formatChatDay(
    timestamp: Long,
    now: Long,
    zoneId: ZoneId,
    locale: Locale,
    todayLabel: String,
    yesterdayLabel: String
): String {
    val date = Instant.ofEpochMilli(timestamp).atZone(zoneId).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()
    return when (ChronoUnit.DAYS.between(date, today)) {
        0L -> todayLabel
        1L -> yesterdayLabel
        else -> if (date.year == today.year) {
            DateTimeFormatter.ofPattern(
                if (locale.language == Locale.CHINESE.language) "M月d日" else "MMM d",
                locale
            ).format(date)
        } else {
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(locale)
                .format(date)
        }
    }
}

@Composable
internal fun ChatLoadingSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.Bottom
    ) {
        repeat(6) { index ->
            val isSelf = index % 3 == 1
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 7.dp),
                horizontalArrangement = if (isSelf) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.Bottom
            ) {
                if (!isSelf) {
                    Surface(
                        modifier = Modifier.size(32.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
                    ) {}
                    Spacer(Modifier.width(8.dp))
                }
                Surface(
                    modifier = Modifier
                        .width(if (index % 2 == 0) 210.dp else 156.dp)
                        .height(if (index % 3 == 0) 58.dp else 44.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
                ) {}
            }
        }
    }
}

@Composable
internal fun ChatStateMessage(
    modifier: Modifier,
    icon: @Composable () -> Unit,
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.padding(horizontal = 36.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(68.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Box(contentAlignment = Alignment.Center) { icon() }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(7.dp))
        Text(
            text = message,
            modifier = Modifier.widthIn(max = 320.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onAction,
                modifier = Modifier
                    .widthIn(min = 156.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(15.dp)
            ) {
                Text(actionLabel, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private const val MESSAGE_GROUP_WINDOW_MS = 5 * 60_000L

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
