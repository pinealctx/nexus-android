package com.pinealctx.nexus.core

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamMessagesTest {
    @Test
    fun `ordered deltas accumulate and duplicate sequences are ignored`() {
        val first = mergeStreamContent(
            existing = MessageContent.Stream(MessageStreamPhase.START),
            incoming = MessageContent.Stream(
                phase = MessageStreamPhase.DELTA,
                sequence = 1,
                delta = "Hello"
            )
        )
        val second = mergeStreamContent(
            existing = first,
            incoming = MessageContent.Stream(
                phase = MessageStreamPhase.DELTA,
                sequence = 2,
                delta = " world"
            )
        )
        val duplicate = mergeStreamContent(
            existing = second,
            incoming = MessageContent.Stream(
                phase = MessageStreamPhase.DELTA,
                sequence = 2,
                delta = " world"
            )
        )

        assertEquals("Hello world", duplicate.accumulatedText)
        assertEquals(2, duplicate.sequence)
    }

    @Test
    fun `terminal update uses authoritative content and rejects late deltas`() {
        val ended = mergeStreamContent(
            existing = MessageContent.Stream(
                phase = MessageStreamPhase.DELTA,
                sequence = 2,
                accumulatedText = "partial"
            ),
            incoming = MessageContent.Stream(
                phase = MessageStreamPhase.END,
                accumulatedText = "complete response",
                contentType = "text/markdown"
            )
        )
        val afterLateDelta = mergeStreamContent(
            existing = ended,
            incoming = MessageContent.Stream(
                phase = MessageStreamPhase.DELTA,
                sequence = 3,
                delta = " ignored"
            )
        )

        assertEquals(MessageStreamPhase.END, afterLateDelta.phase)
        assertEquals("complete response", afterLateDelta.accumulatedText)
        assertEquals("text/markdown", afterLateDelta.contentType)
    }

    @Test
    fun `error keeps partial content and exposes failure reason`() {
        val failed = mergeStreamContent(
            existing = MessageContent.Stream(
                phase = MessageStreamPhase.DELTA,
                sequence = 1,
                accumulatedText = "partial"
            ),
            incoming = MessageContent.Stream(
                phase = MessageStreamPhase.ERROR,
                errorMessage = "upstream timeout"
            )
        )

        assertEquals("partial", failed.accumulatedText)
        assertEquals("upstream timeout", failed.errorMessage)
    }
}
