package com.pinealctx.nexus.ui.screens.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinealctx.nexus.R
import com.pinealctx.nexus.core.MessageData
import com.pinealctx.nexus.core.managers.ReactionManager
import com.shared.v1.ReactionConfig
import com.shared.v1.UserInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ReactionOperation(val emoji: String, val present: Boolean)
data class ReactionsUiState(
    val config: ReactionConfig = ReactionConfig.getDefaultInstance(),
    val pending: Map<Long, ReactionOperation> = emptyMap(),
    val picker: MessageData? = null,
    val details: Pair<MessageData, String>? = null,
    val users: List<UserInfo> = emptyList(),
    val nextUserId: Int = 0,
    val hasMore: Boolean = false,
    val loading: Boolean = false,
    val error: Boolean = false
)

@HiltViewModel
class ReactionsViewModel @Inject constructor(private val manager: ReactionManager) : ViewModel() {
    private val mutableState = MutableStateFlow(ReactionsUiState())
    val state = mutableState.asStateFlow()
    init { viewModelScope.launch {
        try { val config = withContext(Dispatchers.IO) { manager.configuration() }; mutableState.value = mutableState.value.copy(config = config) }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { mutableState.value = mutableState.value.copy(error = true) }
    } }
    fun picker(message: MessageData) { mutableState.value = mutableState.value.copy(picker = message) }
    fun dismiss() { mutableState.value = mutableState.value.copy(picker = null, details = null, error = false) }
    fun toggle(message: MessageData, emoji: String) {
        if (!state.value.config.enabled) return
        if (message.messageId in state.value.pending) return
        val selected = message.reactions?.selectedEmojisList.orEmpty()
        val present = emoji !in selected
        if (present && selected.size >= state.value.config.maxPerUser) { mutableState.value = state.value.copy(error = true); return }
        mutableState.value = state.value.copy(picker = null, pending = state.value.pending + (message.messageId to ReactionOperation(emoji, present)), error = false)
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { manager.set(message.conversationId.toLong(), message.messageId, emoji, present) } }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutableState.value = state.value.copy(error = true) }
            finally { mutableState.value = state.value.copy(pending = state.value.pending - message.messageId) }
        }
    }
    fun details(message: MessageData, emoji: String) {
        mutableState.value = state.value.copy(details = message to emoji, users = emptyList(), nextUserId = 0, hasMore = true, loading = false, error = false)
        loadMore()
    }
    fun loadMore() {
        val target = state.value.details ?: return
        if (state.value.loading) return
        val cursor = state.value.nextUserId
        mutableState.value = state.value.copy(loading = true, error = false)
        viewModelScope.launch {
            try {
                val page = withContext(Dispatchers.IO) { manager.reactors(target.first.conversationId.toLong(), target.first.messageId, target.second, cursor) }
                if (state.value.details != target) return@launch
                mutableState.value = state.value.copy(users = (state.value.users + page.usersList).distinctBy { it.userId }, nextUserId = page.nextUserId, hasMore = page.hasMore, loading = false)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { if (state.value.details == target) mutableState.value = state.value.copy(loading = false, error = true) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ReactionBar(message: MessageData, operation: ReactionOperation?, enabled: Boolean, onToggle: (String) -> Unit, onDetails: (String) -> Unit) {
    val counts = message.reactions?.state?.countsList.orEmpty().associate { it.emoji to it.count }.toMutableMap()
    val selected = message.reactions?.selectedEmojisList.orEmpty().toMutableSet()
    operation?.let {
        val old = it.emoji in selected
        counts[it.emoji] = ((counts[it.emoji] ?: 0) + if (old == it.present) 0 else if (it.present) 1 else -1).coerceAtLeast(0)
        if (it.present) selected.add(it.emoji) else selected.remove(it.emoji)
    }
    FlowRow(Modifier.widthIn(max = 304.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        counts.filterValues { it > 0 }.forEach { (emoji, count) ->
            Surface(color = if (emoji in selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.padding(top = 4.dp).combinedClickable(enabled = operation == null, onClick = { if (enabled) onToggle(emoji) else onDetails(emoji) }, onLongClick = { onDetails(emoji) })) {
                Text("$emoji $count", Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReactionSheets(state: ReactionsUiState, model: ReactionsViewModel) {
    state.picker?.let { message ->
        ModalBottomSheet(onDismissRequest = model::dismiss) {
            Text(stringResource(R.string.reactions_title), Modifier.padding(20.dp), style = MaterialTheme.typography.titleMedium)
            FlowRow(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                state.config.emojisList.forEach { emoji -> FilterChip(selected = emoji in message.reactions?.selectedEmojisList.orEmpty(), onClick = { model.toggle(message, emoji) }, label = { Text(emoji, style = MaterialTheme.typography.headlineSmall) }) }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
    state.details?.let { (_, emoji) ->
        ModalBottomSheet(onDismissRequest = model::dismiss) {
            Text(emoji, Modifier.padding(20.dp), style = MaterialTheme.typography.headlineMedium)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                items(state.users, key = { it.userId }) { user ->
                    ListItem(headlineContent = { Text(user.nickname.ifBlank { user.username }) }, leadingContent = {
                        com.pinealctx.nexus.ui.components.NexusAvatar(id = user.userId, name = user.nickname, avatarUrl = user.avatarUrl, size = 36.dp)
                    })
                }
                if (state.hasMore || state.error) item { TextButton(onClick = model::loadMore, enabled = !state.loading) { Text(stringResource(if (state.error) R.string.conversations_load_more_retry else R.string.conversations_load_more)) } }
                if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
    if (state.error && state.details == null) AlertDialog(onDismissRequest = model::dismiss,
        text = { Text(stringResource(R.string.reactions_failed)) }, confirmButton = { TextButton(onClick = model::dismiss) { Text(stringResource(R.string.confirm)) } })
}
