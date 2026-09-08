package com.pinealctx.nexus.ui.screens.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinealctx.nexus.core.MessageData
import com.pinealctx.nexus.core.TextEntityData
import com.pinealctx.nexus.core.ContactData
import com.pinealctx.nexus.core.managers.GroupManager
import com.pinealctx.nexus.core.AppEventBus
import com.pinealctx.nexus.core.MessageSendScheduler
import com.pinealctx.nexus.core.AgentInfoData
import com.pinealctx.nexus.core.managers.AgentManager
import com.pinealctx.nexus.core.SyncManager
import com.pinealctx.nexus.core.managers.ConversationManager
import com.pinealctx.nexus.core.managers.MediaManager
import com.pinealctx.nexus.core.managers.MessageManager
import com.pinealctx.nexus.core.managers.SearchManager
import com.pinealctx.nexus.core.managers.UserManager
import com.pinealctx.nexus.data.repository.MessageRepository
import com.shared.v1.MediaPurpose
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import com.shared.v1.ConversationType
import java.io.ByteArrayOutputStream
import javax.inject.Inject

data class ChatUiState(
    val agentInfo: AgentInfoData? = null,
    val agentToolsLoadFailed: Boolean = false,
    val isLoadingAgentTools: Boolean = false,
    val mentionCandidates: List<MentionCandidate> = emptyList(),
    val isLoadingMentions: Boolean = false,
    val mentionsLoadFailed: Boolean = false,
    val mentionedUser: ContactData? = null,
    val mentionedUserId: Int? = null,
    val isLoadingMentionedUser: Boolean = false,
    val messages: List<ChatMessageItem> = emptyList(),
    val currentUserId: Int = 0,
    val conversationTitle: String = "",
    val conversationPeerId: Int = 0,
    val conversationAvatarUrl: String? = null,
    val isGroupConversation: Boolean = false,
    val senderNames: Map<Int, String> = emptyMap(),
    val senderAvatarUrls: Map<Int, String> = emptyMap(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val hasCompletedInitialLoad: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val isSending: Boolean = false,
    val mediaUploadName: String? = null,
    val isLocatingMessage: Boolean = false,
    val scrollToMessageId: Long? = null,
    val pendingMessageActionId: Long? = null,
    val mediaUrls: Map<String, String> = emptyMap(),
    val error: String? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val conversationManager: ConversationManager,
    private val messageManager: MessageManager,
    private val mediaManager: MediaManager,
    private val searchManager: SearchManager,
    private val userManager: UserManager,
    private val groupManager: GroupManager,
    private val agentManager: AgentManager,
    private val messageRepository: MessageRepository,
    private val messageSendScheduler: MessageSendScheduler,
    private val syncManager: SyncManager,
    private val appEventBus: AppEventBus,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val conversationId: String = savedStateHandle["conversationId"] ?: ""
    private val searchController = ChatSearchController(viewModelScope) { query, limit, offset ->
        withContext(Dispatchers.IO) {
            searchManager.searchMessages(query, conversationId, limit, offset)
        }
    }
    val searchState = searchController.state
    val initialDraft: String = syncManager.getDraft(conversationId)
    val initialDraftEntities: List<TextEntityData> = syncManager.getDraftEntities(conversationId)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    private var historyRefreshJob: Job? = null
    private var pendingHistoryRefresh = false
    private var markReadJob: Job? = null
    private var senderResolutionJob: Job? = null
    private var mentionedUserJob: Job? = null
    private var revealJob: Job? = null

    fun loadAgentTools() {
        val state = _uiState.value
        if (state.isGroupConversation || state.conversationPeerId <= 0 || state.isLoadingAgentTools) return
        _uiState.value = state.copy(isLoadingAgentTools = true, agentToolsLoadFailed = false)
        viewModelScope.launch {
            try {
                val cached = withContext(Dispatchers.IO) { agentManager.getCachedAgentInfo(state.conversationPeerId) }
                _uiState.value = _uiState.value.copy(agentInfo = cached)
                val agent = withContext(Dispatchers.IO) {
                    val accountType = userManager.getAccountType(state.conversationPeerId)
                        ?: error("Account type unavailable")
                    if (accountType == com.shared.v1.AccountType.ACCOUNT_TYPE_AGENT) {
                        agentManager.fetchAgentInfo(state.conversationPeerId)
                            ?: error("Agent information unavailable")
                    } else null
                }
                _uiState.value = _uiState.value.copy(agentInfo = agent, isLoadingAgentTools = false)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingAgentTools = false, agentToolsLoadFailed = true)
            }
        }
    }

    init {
        syncManager.activeConversationId = conversationId
        observeUpdates()
        loadConversation()
        loadMessages()
        appEventBus.coldStartCompleted()
            .onEach { loadMessages() }
            .launchIn(viewModelScope)
    }

    override fun onCleared() {
        super.onCleared()
        if (syncManager.activeConversationId == conversationId) {
            syncManager.activeConversationId = null
        }
    }

    fun saveDraft(text: String, entities: List<TextEntityData> = emptyList()) {
        syncManager.saveDraft(conversationId, text, entities)
    }

    fun loadMentionCandidates() {
        val state = _uiState.value
        if (!state.isGroupConversation || state.isLoadingMentions) return
        _uiState.value = state.copy(isLoadingMentions = true, mentionsLoadFailed = false)
        viewModelScope.launch {
            try {
                val members = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    groupManager.fetchGroupMembers(state.conversationPeerId)
                }
                _uiState.value = _uiState.value.copy(
                    mentionCandidates = listOf(MentionCandidate(0, context.getString(com.pinealctx.nexus.R.string.chat_mention_all), true)) +
                        members.filter { it.userId != messageManager.currentUserId() }.map {
                            MentionCandidate(it.userId, it.displayName.ifBlank { context.getString(com.pinealctx.nexus.R.string.chat_user_fallback, it.userId) })
                        },
                    isLoadingMentions = false
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingMentions = false, mentionsLoadFailed = true)
            }
        }
    }

    fun showMentionedUser(userId: Int) {
        mentionedUserJob?.cancel()
        _uiState.value = _uiState.value.copy(mentionedUserId = userId, mentionedUser = null, isLoadingMentionedUser = true)
        mentionedUserJob = viewModelScope.launch {
            try {
                val user = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    userManager.batchGetUserInfo(listOf(userId)).firstOrNull()
                }
                _uiState.value = _uiState.value.copy(mentionedUser = user, isLoadingMentionedUser = false)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingMentionedUser = false)
            }
        }
    }

    fun dismissMentionedUser() {
        mentionedUserJob?.cancel()
        _uiState.value = _uiState.value.copy(mentionedUser = null, mentionedUserId = null, isLoadingMentionedUser = false)
    }

    private fun observeUpdates() {
        combine(
            messageRepository.observeMessages(conversationId),
            messageRepository.observeLocalMessages(conversationId)
        ) { remoteMessages, localMessages -> remoteMessages to localMessages }
            .onEach { (remoteMessages, localMessages) ->
                val merged = ChatMessageMerger.merge(remoteMessages, localMessages)
                _uiState.value = _uiState.value.copy(
                    messages = merged,
                    currentUserId = messageManager.currentUserId(),
                    isLoading = if (merged.isNotEmpty()) false else _uiState.value.isLoading,
                    hasCompletedInitialLoad = merged.isNotEmpty() ||
                        _uiState.value.hasCompletedInitialLoad
                )
                scheduleMarkRead(remoteMessages)
                resolveSenderNames(remoteMessages)
            }
            .launchIn(viewModelScope)
    }

    private fun loadMessages() {
        if (historyRefreshJob?.isActive == true) {
            pendingHistoryRefresh = true
            return
        }

        historyRefreshJob = viewModelScope.launch(Dispatchers.IO) {
            val startedAt = SystemClock.elapsedRealtime()
            val hasCachedMessages = _uiState.value.messages.isNotEmpty()
            _uiState.value = _uiState.value.copy(
                isLoading = !hasCachedMessages && !_uiState.value.hasCompletedInitialLoad,
                isRefreshing = hasCachedMessages,
                error = null
            )
            try {
                val page = messageManager.fetchMessagePage(conversationId)
                _uiState.value = _uiState.value.copy(
                    currentUserId = messageManager.currentUserId(),
                    isLoading = false,
                    isRefreshing = false,
                    hasCompletedInitialLoad = true,
                    hasMore = page.hasMore,
                    error = null
                )
                val convId = conversationId.toLongOrNull()
                val recoveredStreams = convId?.let { messageManager.recoverIncompleteStreams(it) } ?: 0
                Log.i(
                    "NexusChat",
                    "History refreshed: conversation=$conversationId messages=${page.messages.size} " +
                        "hasMore=${page.hasMore} recoveredStreams=$recoveredStreams " +
                        "durationMs=${SystemClock.elapsedRealtime() - startedAt}"
                )
            } catch (e: Exception) {
                Log.w("NexusChat", "History refresh failed: conversation=$conversationId", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    hasCompletedInitialLoad = true,
                    pendingMessageActionId = null,
                    error = e.message
                )
            } finally {
                val shouldRefreshAgain = pendingHistoryRefresh
                pendingHistoryRefresh = false
                historyRefreshJob = null
                if (shouldRefreshAgain) loadMessages()
            }
        }
    }

    fun retryLoad() {
        loadMessages()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun loadConversation() {
        viewModelScope.launch(Dispatchers.IO) {
            val convId = conversationId.toLongOrNull() ?: return@launch
            runCatching { conversationManager.getConversation(convId) }
                .onSuccess { conversation ->
                    conversation ?: return@onSuccess
                    val isGroup = conversation.conversationType ==
                        ConversationType.CONVERSATION_TYPE_GROUP
                    _uiState.value = _uiState.value.copy(
                        conversationTitle = conversation.displayName
                            ?.takeIf { it.isNotBlank() }
                            ?: when {
                                isGroup -> context.getString(
                                    com.pinealctx.nexus.R.string.chat_group_fallback,
                                    conversation.conversationId.takeLast(6)
                                )
                                conversation.peerId > 0 -> context.getString(
                                    com.pinealctx.nexus.R.string.chat_user_fallback,
                                    conversation.peerId
                                )
                                else -> context.getString(
                                    com.pinealctx.nexus.R.string.chat_conversation_fallback,
                                    conversation.conversationId.takeLast(6)
                                )
                            },
                        conversationPeerId = conversation.peerId,
                        conversationAvatarUrl = conversation.avatarUrl,
                        isGroupConversation = isGroup
                    )
                    if (conversation.conversationType == ConversationType.CONVERSATION_TYPE_PRIVATE) {
                        withContext(Dispatchers.Main) { loadAgentTools() }
                    }
                }
        }
    }

    private fun scheduleMarkRead(messages: List<MessageData>) {
        if (messages.isEmpty()) return
        markReadJob?.cancel()
        markReadJob = viewModelScope.launch(Dispatchers.IO) {
            runCatching { markCurrentConversationRead(messages) }
        }
    }

    private fun resolveSenderNames(messages: List<MessageData>) {
        val known = _uiState.value.senderNames.keys intersect _uiState.value.senderAvatarUrls.keys
        val missingUserIds = buildSet {
            messages.forEach { message ->
                if (message.senderId > 0 && message.senderId !in known) add(message.senderId)
                (message.content as? com.pinealctx.nexus.core.MessageContent.GroupEvent)?.let { event ->
                    event.memberIds.filterTo(this) { it > 0 && it !in known }
                    event.inviterId?.takeIf { it > 0 && it !in known }?.let(::add)
                    event.operatorId?.takeIf { it > 0 && it !in known }?.let(::add)
                }
                message.replyContext?.senderId
                    ?.takeIf { it > 0 && it !in known }
                    ?.let(::add)
            }
        }
        if (missingUserIds.isEmpty()) return

        senderResolutionJob?.cancel()
        senderResolutionJob = viewModelScope.launch(Dispatchers.IO) {
            runCatching { userManager.batchGetUserInfo(missingUserIds.toList()) }
                .onSuccess { users ->
                    val resolved = users.associate { user ->
                        user.userId to (user.alias?.takeIf { it.isNotBlank() }
                            ?: user.nickname.takeIf { it.isNotBlank() }
                            ?: user.username.takeIf { it.isNotBlank() }
                            ?: "User ${user.userId}")
                    }
                    val avatars = users.associate { user -> user.userId to user.avatarUrl }
                    _uiState.value = _uiState.value.copy(
                        senderNames = _uiState.value.senderNames + resolved,
                        senderAvatarUrls = _uiState.value.senderAvatarUrls + avatars
                    )
                }
        }
    }

    private fun refreshLocalMessages(isSending: Boolean = _uiState.value.isSending, error: String? = _uiState.value.error) {
        val remoteMessages = _uiState.value.messages
            .filterIsInstance<ChatMessageItem.Remote>()
            .map { it.data }
        val localMessages = messageManager.getLocalMessages(conversationId)
        _uiState.value = _uiState.value.copy(
            messages = ChatMessageMerger.merge(remoteMessages, localMessages),
            isSending = isSending,
            error = error
        )
    }

    private suspend fun markCurrentConversationRead(messages: List<MessageData>) {
        val convId = conversationId.toLongOrNull() ?: return
        val conversation = conversationManager
            .getConversations()
            .firstOrNull { it.conversationId == conversationId }
        val latestMessageId = maxOf(
            conversation?.lastMessageId ?: 0L,
            messages.maxOfOrNull { it.messageId } ?: 0L
        )
        if (latestMessageId <= 0L || latestMessageId <= (conversation?.lastReadMessageId ?: 0L)) {
            return
        }
        conversationManager.markAsRead(convId, latestMessageId)
    }

    fun sendMessage(text: String, replyToMessageId: Long? = null, entities: List<TextEntityData> = emptyList()) {
        if (text.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isSending = true)
            val convId = conversationId.toLongOrNull()
            if (convId == null) {
                _uiState.value = _uiState.value.copy(isSending = false)
                return@launch
            }
            val localMessage = messageManager.enqueueTextMessage(convId, text, replyToMessageId, entities)
            refreshLocalMessages(isSending = true, error = null)
            try {
                messageSendScheduler.enqueue(localMessage.clientMessageId)
                refreshLocalMessages(isSending = false, error = null)
            } catch (e: Exception) {
                refreshLocalMessages(isSending = false, error = e.message)
            }
        }
    }

    fun retryMessage(clientMessageId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            messageManager.markLocalMessageSending(clientMessageId)
            refreshLocalMessages(isSending = true, error = null)
            messageSendScheduler.enqueue(clientMessageId)
            refreshLocalMessages(isSending = false, error = null)
        }
    }

    fun submitCardAction(messageId: Long, actionType: String, actionData: String) {
        val convId = conversationId.toLongOrNull() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                messageManager.submitCardAction(
                    conversationId = convId,
                    messageId = messageId,
                    actionData = actionData,
                    verb = actionType
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(error = error.message)
            }
        }
    }

    fun editMessage(messageId: Long, text: String, entities: List<TextEntityData> = emptyList()) {
        if (text.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val convId = conversationId.toLongOrNull() ?: return@launch
            _uiState.value = _uiState.value.copy(pendingMessageActionId = messageId, error = null)
            runCatching {
                messageManager.editMessage(convId, messageId, text, entities)
            }.onSuccess {
                _uiState.value = _uiState.value.copy(pendingMessageActionId = null)
                loadMessages()
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(pendingMessageActionId = null, error = error.message)
            }
        }
    }

    fun recallMessage(messageId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val convId = conversationId.toLongOrNull() ?: return@launch
            _uiState.value = _uiState.value.copy(pendingMessageActionId = messageId, error = null)
            runCatching {
                messageManager.recallMessage(convId, messageId)
            }.onSuccess {
                _uiState.value = _uiState.value.copy(pendingMessageActionId = null)
                loadMessages()
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(pendingMessageActionId = null, error = error.message)
            }
        }
    }

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val convId = conversationId.toLongOrNull() ?: return@launch
            _uiState.value = _uiState.value.copy(pendingMessageActionId = messageId, error = null)
            runCatching {
                messageManager.deleteMessages(convId, listOf(messageId))
            }.onSuccess {
                _uiState.value = _uiState.value.copy(pendingMessageActionId = null)
                loadMessages()
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(pendingMessageActionId = null, error = error.message)
            }
        }
    }

    fun revealMessage(messageId: Long) {
        revealJob?.cancel()
        _uiState.value = _uiState.value.copy(isLocatingMessage = true, scrollToMessageId = null, error = null)
        revealJob = viewModelScope.launch {
            try {
                val found = ChatMessageIndex.remoteIndexOf(_uiState.value.messages, messageId) >= 0 ||
                    withContext(Dispatchers.IO) { loadUntilMessage(messageId) }
                _uiState.value = _uiState.value.copy(
                    isLocatingMessage = false,
                    scrollToMessageId = if (found) messageId else null,
                    error = if (found) null else context.getString(com.pinealctx.nexus.R.string.chat_search_locate_missing)
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLocatingMessage = false,
                    error = context.getString(com.pinealctx.nexus.R.string.chat_search_locate_failed)
                )
            }
        }
    }

    fun consumeScrollTarget(messageId: Long) {
        if (_uiState.value.scrollToMessageId == messageId) {
            _uiState.value = _uiState.value.copy(scrollToMessageId = null)
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isRefreshing || state.isLoadingMore || !state.hasMore) return
        val oldest = _uiState.value.messages
            .filterIsInstance<ChatMessageItem.Remote>()
            .lastOrNull()
            ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            try {
                val page = messageManager.fetchMessagePage(
                    conversationId = conversationId,
                    beforeId = oldest.data.messageId
                )
                _uiState.value = _uiState.value.copy(
                    isLoadingMore = false,
                    hasMore = page.hasMore,
                    error = null
                )
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingMore = false,
                    error = error.message
                )
            }
        }
    }

    fun resolveMediaUrl(fileId: String) {
        if (fileId.isBlank() || _uiState.value.mediaUrls.containsKey(fileId)) return
        mediaManager.getCachedMediaUrl(fileId)?.let { cachedUrl ->
            _uiState.value = _uiState.value.copy(
                mediaUrls = _uiState.value.mediaUrls + (fileId to cachedUrl)
            )
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { mediaManager.getDownloadUrl(fileId) }
                .onSuccess { url ->
                    _uiState.value = _uiState.value.copy(
                        mediaUrls = _uiState.value.mediaUrls + (fileId to url)
                    )
                }
        }
    }

    fun sendVisualMedia(uri: Uri) {
        sendMedia(uri, visualOnly = true)
    }

    fun sendFile(uri: Uri) {
        sendMedia(uri, visualOnly = false)
    }

    fun sendVoiceRecording(recording: VoiceRecording) {
        viewModelScope.launch(Dispatchers.IO) {
            val convId = conversationId.toLongOrNull()
            if (convId == null) {
                recording.file.delete()
                return@launch
            }
            val fileName = "voice-${System.currentTimeMillis()}.m4a"
            _uiState.value = _uiState.value.copy(
                isSending = true,
                mediaUploadName = context.getString(com.pinealctx.nexus.R.string.voice_message),
                error = null
            )
            try {
                val uploaded = recording.file.inputStream().use { input ->
                    mediaManager.uploadStream(
                        input = input,
                        fileName = fileName,
                        contentType = "audio/mp4",
                        size = recording.file.length(),
                        purpose = MediaPurpose.MEDIA_PURPOSE_MESSAGE
                    )
                }
                val localMessage = messageManager.enqueueAudioMessage(
                    conversationId = convId,
                    fileId = uploaded.fileId,
                    durationMs = uploaded.durationMs.toInt().takeIf { it > 0 }
                        ?: recording.durationMs,
                    sizeBytes = uploaded.size
                )
                refreshLocalMessages(isSending = true, error = null)
                messageSendScheduler.enqueue(localMessage.clientMessageId)
                refreshLocalMessages(isSending = false, error = null)
                _uiState.value = _uiState.value.copy(mediaUploadName = null)
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSending = false,
                    mediaUploadName = null,
                    error = context.getString(
                        com.pinealctx.nexus.R.string.media_send_failed,
                        error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
                    )
                )
            } finally {
                recording.file.delete()
            }
        }
    }

    private fun sendMedia(uri: Uri, visualOnly: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val convId = conversationId.toLongOrNull() ?: return@launch
            _uiState.value = _uiState.value.copy(
                isSending = true,
                mediaUploadName = context.getString(com.pinealctx.nexus.R.string.media_attachment),
                error = null
            )
            try {
                val metadata = readMediaMetadata(uri)
                _uiState.value = _uiState.value.copy(mediaUploadName = metadata.fileName)
                Log.i(
                    "NexusMedia",
                    "Selected media prepared: type=${metadata.contentType} size=${metadata.size} " +
                        "dimensions=${metadata.width}x${metadata.height}"
                )
                if (visualOnly && !metadata.contentType.startsWith("image/") &&
                    !metadata.contentType.startsWith("video/")) {
                    throw IllegalArgumentException("Selected item is not an image or video")
                }
                val uploaded = context.contentResolver.openInputStream(uri)?.use { input ->
                    mediaManager.uploadStream(
                        input = input,
                        fileName = metadata.fileName,
                        contentType = metadata.contentType,
                        size = metadata.size,
                        purpose = MediaPurpose.MEDIA_PURPOSE_MESSAGE
                    )
                } ?: throw IllegalStateException("Unable to open selected file")
                Log.i("NexusMedia", "Selected media uploaded: size=${uploaded.size}")

                val thumbnailFileId = if (
                    metadata.contentType.startsWith("video/") && uploaded.thumbnailFileId.isBlank()
                ) {
                    runCatching {
                        val thumbnail = createVideoThumbnail(uri) ?: return@runCatching ""
                        mediaManager.uploadFile(
                            data = thumbnail,
                            fileName = "${metadata.fileName.substringBeforeLast('.')}-thumbnail.jpg",
                            contentType = "image/jpeg",
                            purpose = MediaPurpose.MEDIA_PURPOSE_MESSAGE
                        ).fileId
                    }.onFailure { error ->
                        Log.w("NexusMedia", "Video thumbnail generation failed", error)
                    }.getOrDefault("")
                } else {
                    uploaded.thumbnailFileId
                }

                val localMessage = when {
                    metadata.contentType.startsWith("image/") -> messageManager.enqueueImageMessage(
                        convId,
                        uploaded.fileId,
                        metadata.width,
                        metadata.height
                    )
                    metadata.contentType.startsWith("video/") -> messageManager.enqueueVideoMessage(
                        convId,
                        uploaded.fileId,
                        uploaded.width.takeIf { it > 0 } ?: metadata.width,
                        uploaded.height.takeIf { it > 0 } ?: metadata.height,
                        uploaded.durationMs.toInt().takeIf { it > 0 } ?: metadata.durationMs,
                        thumbnailFileId,
                        uploaded.size
                    )
                    metadata.contentType.startsWith("audio/") -> messageManager.enqueueAudioMessage(
                        convId,
                        uploaded.fileId,
                        uploaded.durationMs.toInt().takeIf { it > 0 } ?: metadata.durationMs,
                        uploaded.size
                    )
                    else -> messageManager.enqueueFileMessage(
                        conversationId = convId,
                        fileId = uploaded.fileId,
                        name = metadata.fileName,
                        size = uploaded.size,
                        mimeType = metadata.contentType
                    )
                }
                refreshLocalMessages(isSending = true, error = null)
                Log.i("NexusMedia", "Media message queued: clientMessageId=${localMessage.clientMessageId}")
                messageSendScheduler.enqueue(localMessage.clientMessageId)
                refreshLocalMessages(isSending = false, error = null)
                _uiState.value = _uiState.value.copy(mediaUploadName = null)
            } catch (error: Exception) {
                Log.e("NexusMedia", "Selected media send failed", error)
                _uiState.value = _uiState.value.copy(
                    isSending = false,
                    mediaUploadName = null,
                    error = context.getString(
                        com.pinealctx.nexus.R.string.media_send_failed,
                        error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
                    )
                )
            }
        }
    }

    private fun createVideoThumbnail(uri: Uri): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val frame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: return null
            val largestSide = maxOf(frame.width, frame.height)
            val scaled = if (largestSide > VIDEO_THUMBNAIL_MAX_PX) {
                val scale = VIDEO_THUMBNAIL_MAX_PX.toFloat() / largestSide
                Bitmap.createScaledBitmap(
                    frame,
                    (frame.width * scale).toInt().coerceAtLeast(1),
                    (frame.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                frame
            }
            try {
                ByteArrayOutputStream().use { output ->
                    check(scaled.compress(Bitmap.CompressFormat.JPEG, VIDEO_THUMBNAIL_QUALITY, output)) {
                        "Unable to encode the video thumbnail"
                    }
                    output.toByteArray()
                }
            } finally {
                if (scaled !== frame) scaled.recycle()
                frame.recycle()
            }
        } finally {
            retriever.release()
        }
    }

    private fun readMediaMetadata(uri: Uri): PickedMediaMetadata {
        val resolver = context.contentResolver
        var fileName = uri.lastPathSegment ?: "attachment"
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) fileName = cursor.getString(nameIndex)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        if (size < 0) size = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        require(size >= 0) { "Unable to determine selected file size" }

        val contentType = resolver.getType(uri) ?: "application/octet-stream"
        var width = 0
        var height = 0
        var durationMs = 0
        if (contentType.startsWith("image/")) {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            width = options.outWidth.coerceAtLeast(0)
            height = options.outHeight.coerceAtLeast(0)
        } else if (contentType.startsWith("video/") || contentType.startsWith("audio/")) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: 0
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull() ?: 0
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull() ?: 0
                val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                    ?.toIntOrNull() ?: 0
                if (rotation == 90 || rotation == 270) {
                    val unrotatedWidth = width
                    width = height
                    height = unrotatedWidth
                }
            } finally {
                retriever.release()
            }
        }
        return PickedMediaMetadata(fileName, contentType, size, width, height, durationMs)
    }

    private suspend fun loadUntilMessage(messageId: Long, maxPages: Int = 20): Boolean {
        var remoteMessages = _uiState.value.messages
            .filterIsInstance<ChatMessageItem.Remote>()
            .map { it.data }
        if (remoteMessages.any { it.messageId == messageId }) return true

        repeat(maxPages) {
            currentCoroutineContext().ensureActive()
            val oldest = remoteMessages.minByOrNull { it.messageId } ?: return false
            val cached = messageManager.getCachedMessages(conversationId, beforeId = oldest.messageId)
            val canUseCache = cached.any { it.messageId == messageId } ||
                (cached.lastOrNull()?.messageId ?: Long.MIN_VALUE) > messageId
            val page = if (!canUseCache) messageManager.fetchMessagePage(conversationId, beforeId = oldest.messageId) else null
            val older = page?.messages ?: cached
            if (older.isEmpty()) return false

            remoteMessages = mergeRemoteMessages(remoteMessages, older)
            val localMessages = messageManager.getLocalMessages(conversationId)
            currentCoroutineContext().ensureActive()
            _uiState.value = _uiState.value.copy(
                messages = ChatMessageMerger.merge(remoteMessages, localMessages),
                currentUserId = messageManager.currentUserId()
            )
            if (remoteMessages.any { it.messageId == messageId }) return true
            if (page != null && !page.hasMore) return false
        }
        return false
    }

    private fun mergeRemoteMessages(
        current: List<MessageData>,
        incoming: List<MessageData>
    ): List<MessageData> {
        return (current + incoming)
            .distinctBy { it.messageId }
            .sortedByDescending { it.messageId }
    }

    fun setSearchQuery(query: String) = searchController.setQuery(query)
    fun closeSearch() = searchController.close()
    fun loadMoreSearchResults() = searchController.loadMore()
    fun retrySearch() = searchController.retry()
}

private data class PickedMediaMetadata(
    val fileName: String,
    val contentType: String,
    val size: Long,
    val width: Int,
    val height: Int,
    val durationMs: Int
)

private const val VIDEO_THUMBNAIL_MAX_PX = 640
private const val VIDEO_THUMBNAIL_QUALITY = 82
