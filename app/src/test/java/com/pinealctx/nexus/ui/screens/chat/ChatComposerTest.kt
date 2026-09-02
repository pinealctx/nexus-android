package com.pinealctx.nexus.ui.screens.chat

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatComposerTest {
    @Test
    fun emojiIsInsertedAtCursorAndCursorMovesAfterEmoji() {
        val value = TextFieldValue("hello world", TextRange(5))

        val result = insertTextAtSelection(value, "🙂")

        assertEquals("hello🙂 world", result.text)
        assertEquals(7, result.selection.start)
        assertEquals(7, result.selection.end)
    }

    @Test
    fun emojiReplacesSelectedText() {
        val value = TextFieldValue("hello world", TextRange(6, 11))

        val result = insertTextAtSelection(value, "👋")

        assertEquals("hello 👋", result.text)
        assertEquals(8, result.selection.start)
        assertEquals(8, result.selection.end)
    }
}
