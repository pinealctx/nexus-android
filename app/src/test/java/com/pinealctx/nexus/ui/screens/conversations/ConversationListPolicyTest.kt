package com.pinealctx.nexus.ui.screens.conversations

import com.pinealctx.nexus.core.ConversationData
import com.shared.v1.ConversationType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

class ConversationListPolicyTest {
    @Test
    fun contentAlwaysTakesPriorityOverTransientRefreshState() {
        val state = ConversationListUiState(
            conversations = listOf(conversation()),
            isLoading = true,
            error = "offline"
        )

        assertEquals(ConversationListPhase.CONTENT, resolveConversationListPhase(state))
    }

    @Test
    fun emptyListDistinguishesLoadingFailureAndGenuineEmptyState() {
        assertEquals(
            ConversationListPhase.LOADING,
            resolveConversationListPhase(ConversationListUiState())
        )
        assertEquals(
            ConversationListPhase.ERROR,
            resolveConversationListPhase(
                ConversationListUiState(
                    isLoading = false,
                    hasCompletedInitialLoad = true,
                    error = "offline"
                )
            )
        )
        assertEquals(
            ConversationListPhase.EMPTY,
            resolveConversationListPhase(
                ConversationListUiState(
                    isLoading = false,
                    hasCompletedInitialLoad = true
                )
            )
        )
        assertEquals(
            ConversationListPhase.LOADING,
            resolveConversationListPhase(
                ConversationListUiState(
                    isLoading = false,
                    isSyncing = true,
                    hasCompletedInitialLoad = true
                )
            )
        )
    }

    @Test
    fun conversationTimeUsesHumanFriendlyCalendarBuckets() {
        val now = Instant.parse("2026-09-03T12:00:00Z").toEpochMilli()

        assertEquals("09:05", formatTime("2026-09-03T09:05:00Z", now))
        assertEquals("Yesterday", formatTime("2026-09-02T23:55:00Z", now))
        assertEquals("Mon", formatTime("2026-08-31T10:00:00Z", now))
        assertEquals("08/01", formatTime("2026-08-01T10:00:00Z", now))
        assertEquals("2025/12/31", formatTime("2025-12-31T10:00:00Z", now))
    }

    private fun formatTime(value: String, now: Long): String =
        formatConversationTime(
            timestamp = Instant.parse(value).toEpochMilli(),
            now = now,
            zoneId = ZoneOffset.UTC,
            locale = Locale.US,
            yesterdayLabel = "Yesterday"
        )

    private fun conversation() = ConversationData(
        conversationId = "1001",
        conversationType = ConversationType.CONVERSATION_TYPE_PRIVATE,
        peerId = 42,
        displayName = "Nexus User",
        avatarUrl = null,
        lastMessageId = 2,
        lastMessageTime = 1,
        lastMessageContent = "Hello",
        isMuted = false,
        lastReadMessageId = 1
    )
}
