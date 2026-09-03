package com.pinealctx.nexus.ui.screens.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamMessageRenderPolicyTest {
    @Test
    fun `unspecified and plain stream content use plain text rendering`() {
        assertFalse(streamContentUsesMarkdown(""))
        assertFalse(streamContentUsesMarkdown("text/plain"))
        assertFalse(streamContentUsesMarkdown("application/json"))
    }

    @Test
    fun `explicit markdown content type uses markdown rendering`() {
        assertTrue(streamContentUsesMarkdown("text/markdown"))
        assertTrue(streamContentUsesMarkdown("TEXT/MARKDOWN; charset=utf-8"))
    }
}
