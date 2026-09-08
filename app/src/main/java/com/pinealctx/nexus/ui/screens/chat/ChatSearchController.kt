package com.pinealctx.nexus.ui.screens.chat

import com.pinealctx.nexus.core.MessageSearchResultData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatSearchState(
    val query: String = "",
    val results: List<MessageSearchResultData> = emptyList(),
    val isLoading: Boolean = false,
    val hasSearched: Boolean = false,
    val hasMore: Boolean = false,
    val failed: Boolean = false
)

internal class ChatSearchController(
    private val scope: CoroutineScope,
    private val debounceMillis: Long = 300,
    private val search: suspend (query: String, limit: Int, offset: Int) -> List<MessageSearchResultData>
) {
    private val mutableState = MutableStateFlow(ChatSearchState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private var generation = 0
    private var nextOffset = 0

    fun setQuery(query: String) {
        job?.cancel()
        generation++
        nextOffset = 0
        mutableState.value = ChatSearchState(query = query, isLoading = query.isNotBlank())
        if (query.isNotBlank()) loadPage(debounce = true)
    }

    fun close() = setQuery("")

    fun loadMore() {
        val current = state.value
        if (!current.isLoading && current.hasMore) loadPage()
    }

    fun retry() {
        if (state.value.failed && !state.value.isLoading) loadPage()
    }

    private fun loadPage(debounce: Boolean = false) {
        val current = state.value
        val requestGeneration = generation
        val offset = nextOffset
        mutableState.value = current.copy(isLoading = true, failed = false)
        job = scope.launch {
            try {
                if (debounce) delay(debounceMillis)
                val page = search(current.query.trim(), PageSize + 1, offset)
                if (generation != requestGeneration) return@launch
                nextOffset = offset + minOf(PageSize, page.size)
                mutableState.value = current.copy(
                    results = (current.results + page.take(PageSize)).distinctBy { it.messageId },
                    isLoading = false,
                    hasSearched = true,
                    hasMore = page.size > PageSize,
                    failed = false
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (generation == requestGeneration) mutableState.value = current.copy(isLoading = false, failed = true)
            }
        }
    }

    companion object { const val PageSize = 30 }
}
