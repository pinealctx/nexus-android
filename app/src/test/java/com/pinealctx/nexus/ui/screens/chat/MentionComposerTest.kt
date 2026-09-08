package com.pinealctx.nexus.ui.screens.chat

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.pinealctx.nexus.core.adjustTextEntities
import org.junit.Assert.*
import org.junit.Test

class MentionComposerTest {
    @Test
    fun `mention trigger respects cursor email and IME composition`() {
        assertNull(mentionQuery(TextFieldValue("a@b.com", TextRange(3))))
        assertNull(mentionQuery(TextFieldValue("@", TextRange(0))))
        assertNull(mentionQuery(TextFieldValue("@中", TextRange(2), TextRange(1, 2))))
        assertEquals(MentionQuery(3, 5, "A"), mentionQuery(TextFieldValue("😀 @A hello", TextRange(5))))
    }

    @Test
    fun `selecting duplicate names preserves separate user IDs and cursor position`() {
        val (first, entities) = insertMention(TextFieldValue("@", TextRange(1)), emptyList(), MentionCandidate(7, "Alex"))
        assertEquals("@Alex ", first.text)
        assertEquals(TextRange(6), first.selection)
        val secondInput = TextFieldValue(first.text + "@", TextRange(7))
        val (second, finalEntities) = insertMention(secondInput, entities, MentionCandidate(9, "Alex"))
        assertEquals("@Alex @Alex ", second.text)
        assertEquals(listOf(7, 9), finalEntities.map { it.userId })
        assertEquals(listOf(0, 6), finalEntities.map { it.offset })
        assertEquals(listOf(finalEntities.first()), adjustTextEntities(second.text, "@Alex @Al ", finalEntities))
    }
}
