package com.pinealctx.nexus.ui.screens.chat

import com.pinealctx.nexus.core.MessageSearchResultData
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class ChatSearchControllerTest {
    @Test
    fun `pagination probes one extra result without losing it`() = runBlocking {
        val calls = mutableListOf<Pair<Int, Int>>()
        val controller = ChatSearchController(this, 0) { _, limit, offset ->
            calls += limit to offset
            (1L..35L).map(::result).drop(offset).take(limit)
        }
        controller.setQuery("test")
        controller.awaitReady()
        assertEquals(30, controller.state.value.results.size)
        assertTrue(controller.state.value.hasMore)
        controller.loadMore()
        controller.awaitReady()
        assertEquals((1L..35L).toList(), controller.state.value.results.map { it.messageId })
        assertFalse(controller.state.value.hasMore)
        assertEquals(listOf(31 to 0, 31 to 30), calls)
    }

    @Test
    fun `failure retains earlier pages and retry resumes same offset`() = runBlocking {
        var fail = true
        val controller = ChatSearchController(this, 0) { _, limit, offset ->
            if (offset > 0 && fail) error("Unavailable")
            (1L..35L).map(::result).drop(offset).take(limit)
        }
        controller.setQuery("test")
        controller.awaitReady()
        controller.loadMore()
        controller.awaitReady()
        assertTrue(controller.state.value.failed)
        assertEquals(30, controller.state.value.results.size)
        fail = false
        controller.retry()
        controller.awaitReady()
        assertFalse(controller.state.value.failed)
        assertEquals(35, controller.state.value.results.size)
    }

    @Test
    fun `late response cannot replace newer query or reopen closed search`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val controller = ChatSearchController(this, 0) { query, _, _ ->
            if (query == "old") withContext(NonCancellable) {
                started.complete(Unit)
                release.await()
                listOf(result(1))
            } else listOf(result(2))
        }
        controller.setQuery("old")
        started.await()
        controller.setQuery("new")
        controller.awaitReady()
        assertEquals(2L, controller.state.value.results.single().messageId)
        controller.close()
        release.complete(Unit)
        yield()
        assertEquals(ChatSearchState(), controller.state.value)
    }

    @Test
    fun `blank search skips backend and distinguishes empty results from failure`() = runBlocking {
        var calls = 0
        val controller = ChatSearchController(this, 0) { _, _, _ -> calls++; emptyList() }
        controller.setQuery(" ")
        yield()
        assertEquals(0, calls)
        assertFalse(controller.state.value.hasSearched)
        controller.setQuery("none")
        controller.awaitReady()
        assertTrue(controller.state.value.hasSearched)
        assertFalse(controller.state.value.failed)
    }

    @Test
    fun `rapid queries are debounced`() = runBlocking {
        val calls = mutableListOf<String>()
        val controller = ChatSearchController(this, 10) { query, _, _ -> calls += query; emptyList() }
        controller.setQuery("a")
        controller.setQuery("ab")
        controller.awaitReady()
        assertEquals(listOf("ab"), calls)
    }

    private suspend fun ChatSearchController.awaitReady() = withTimeout(2_000) {
        state.first { !it.isLoading }
    }

    private fun result(id: Long) = MessageSearchResultData("100", id, 7, "test", id)
}
