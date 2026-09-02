package com.pinealctx.nexus.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SyncSequencePolicyTest {
    @Test
    fun `duplicate and stale updates are ignored`() {
        var applied = false
        var committedSn: Int? = null

        val decision = applySequencedUpdate(10, 10, true, { applied = true }, { committedSn = it })

        assertEquals(SyncSequenceDecision.IGNORE, decision)
        assertEquals(false, applied)
        assertEquals(null, committedSn)
    }

    @Test
    fun `gap requests difference without applying update`() {
        var applied = false

        val decision = applySequencedUpdate(10, 12, true, { applied = true }, {})

        assertEquals(SyncSequenceDecision.FETCH_GAP, decision)
        assertEquals(false, applied)
    }

    @Test
    fun `successful update commits sn after applying`() {
        val events = mutableListOf<String>()

        val decision = applySequencedUpdate(
            currentSn = 10,
            incomingSn = 11,
            allowGap = true,
            apply = { events += "apply" },
            commitSn = { events += "commit:$it" }
        )

        assertEquals(SyncSequenceDecision.APPLY, decision)
        assertEquals(listOf("apply", "commit:11"), events)
    }

    @Test
    fun `failed update never advances sn`() {
        var committedSn: Int? = null

        assertThrows(IllegalStateException::class.java) {
            applySequencedUpdate(
                currentSn = 10,
                incomingSn = 11,
                allowGap = true,
                apply = { error("write failed") },
                commitSn = { committedSn = it }
            )
        }

        assertEquals(null, committedSn)
    }
}
