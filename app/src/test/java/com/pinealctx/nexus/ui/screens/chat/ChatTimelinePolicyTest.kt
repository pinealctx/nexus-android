package com.pinealctx.nexus.ui.screens.chat

import com.pinealctx.nexus.core.MessageContent
import com.pinealctx.nexus.core.MessageData
import com.pinealctx.nexus.core.LocalMessageData
import com.pinealctx.nexus.core.MessageSendState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

class ChatTimelinePolicyTest {
    @Test
    fun timelineAddsOneSeparatorForEachCalendarDay() {
        val messages = listOf(
            message(3, 7, "2026-09-03T10:05:00Z"),
            message(2, 7, "2026-09-03T10:00:00Z"),
            message(1, 8, "2026-09-02T23:55:00Z")
        )

        val timeline = buildChatTimeline(messages, ZoneOffset.UTC)
        val separators = timeline.filterIsInstance<ChatTimelineItem.DaySeparator>()

        assertEquals(2, separators.size)
        assertEquals(5, timeline.size)
    }

    @Test
    fun consecutiveMessagesOnlyShowIdentityAtStartOfVisualGroup() {
        val messages = listOf(
            message(3, 7, "2026-09-03T10:02:00Z"),
            message(2, 7, "2026-09-03T10:01:00Z"),
            message(1, 8, "2026-09-03T10:00:00Z")
        )

        val entries = buildChatTimeline(messages, ZoneOffset.UTC)
            .filterIsInstance<ChatTimelineItem.Message>()

        assertFalse(entries[0].showSenderIdentity)
        assertTrue(entries[1].showSenderIdentity)
        assertTrue(entries[2].showSenderIdentity)
    }

    @Test
    fun senderIdentityRestartsAfterFiveMinuteGap() {
        val messages = listOf(
            message(2, 7, "2026-09-03T10:07:00Z"),
            message(1, 7, "2026-09-03T10:00:00Z")
        )

        val entries = buildChatTimeline(messages, ZoneOffset.UTC)
            .filterIsInstance<ChatTimelineItem.Message>()

        assertTrue(entries.all { it.showSenderIdentity })
    }

    @Test
    fun senderIdentityRestartsAcrossDateSeparator() {
        val messages = listOf(
            message(2, 7, "2026-09-03T00:01:00Z"),
            message(1, 7, "2026-09-02T23:59:00Z")
        )

        val entries = buildChatTimeline(messages, ZoneOffset.UTC)
            .filterIsInstance<ChatTimelineItem.Message>()

        assertTrue(entries.all { it.showSenderIdentity })
    }

    @Test
    fun dateLabelsUseFriendlyCalendarBuckets() {
        val now = Instant.parse("2026-09-03T12:00:00Z").toEpochMilli()

        assertEquals("Today", formatDay("2026-09-03T01:00:00Z", now))
        assertEquals("Yesterday", formatDay("2026-09-02T23:00:00Z", now))
        assertEquals("Aug 31", formatDay("2026-08-31T10:00:00Z", now))
        assertEquals("Dec 31, 2025", formatDay("2025-12-31T10:00:00Z", now))
    }

    @Test
    fun newestOutgoingLocalMessageDrivesScrollToLatest() {
        val messages = listOf(
            localMessage(clientMessageId = 103, senderId = 7),
            localMessage(clientMessageId = 102, senderId = 9),
            localMessage(clientMessageId = 101, senderId = 7)
        )

        assertEquals(103L, findNewestOutgoingLocalMessageId(messages, currentUserId = 7))
        assertEquals(102L, findNewestOutgoingLocalMessageId(messages, currentUserId = 9))
        assertEquals(null, findNewestOutgoingLocalMessageId(emptyList(), currentUserId = 7))
    }

    private fun message(messageId: Long, senderId: Int, timestamp: String) =
        ChatMessageItem.Remote(
            MessageData(
                conversationId = "100",
                messageId = messageId,
                senderId = senderId,
                content = MessageContent.Text("Message $messageId"),
                replyToMessageId = null,
                replyContext = null,
                createdAt = Instant.parse(timestamp).toEpochMilli(),
                edited = false,
                recalled = false
            )
        )

    private fun localMessage(clientMessageId: Long, senderId: Int) = ChatMessageItem.Local(
        LocalMessageData(
            clientMessageId = clientMessageId,
            conversationId = "100",
            serverMessageId = null,
            senderId = senderId,
            content = MessageContent.Text("Message $clientMessageId"),
            replyToMessageId = null,
            createdAt = clientMessageId,
            sendState = MessageSendState.SENDING
        )
    )

    private fun formatDay(timestamp: String, now: Long): String = formatChatDay(
        timestamp = Instant.parse(timestamp).toEpochMilli(),
        now = now,
        zoneId = ZoneOffset.UTC,
        locale = Locale.US,
        todayLabel = "Today",
        yesterdayLabel = "Yesterday"
    )
}
