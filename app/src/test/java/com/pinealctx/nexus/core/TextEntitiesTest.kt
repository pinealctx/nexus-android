package com.pinealctx.nexus.core

import com.shared.v1.MessageEntityType.*
import org.junit.Assert.*
import org.junit.Test

class TextEntitiesTest {
    @Test
    fun `emoji offsets use desktop UTF16 positions and malformed ranges are ignored`() {
        val text = "😀 @Alice"
        val mention = TextEntityData(MESSAGE_ENTITY_TYPE_MENTION, 3, 6, userId = 7)
        assertEquals(listOf(mention), validTextEntities(text, listOf(
            mention,
            mention.copy(offset = -1),
            mention.copy(length = Int.MAX_VALUE),
            mention.copy(offset = 1, length = 1)
        )))
    }

    @Test
    fun `edits preserve exact mention identity and drop touched spans`() {
        val old = "@Alex @Alex"
        val first = TextEntityData(MESSAGE_ENTITY_TYPE_MENTION, 0, 5, userId = 1)
        val second = first.copy(offset = 6, userId = 2)
        assertEquals(listOf(second.copy(offset = 0)), adjustTextEntities(old, "@Alex", listOf(first, second), editStartHint = 0))
        assertEquals(listOf(first.copy(offset = 3), second.copy(offset = 9)),
            adjustTextEntities(old, "😀 $old", listOf(first, second)))
        assertEquals(listOf(second), adjustTextEntities(old, "@Alan @Alex", listOf(first, second)))
    }

    @Test
    fun `detected URLs avoid existing entity ranges and exclude punctuation`() {
        val text = "😀 https://example.com, www.example.org!"
        val links = withDetectedLinks(text, emptyList())
        assertEquals(listOf(3, 24), links.map { it.offset })
        assertEquals(listOf("https://example.com", "https://www.example.org"), links.map { it.value })
        assertEquals(links, withDetectedLinks(text, links))
        assertNull(safeWebLink("javascript:alert(1)"))
        assertNull(safeWebLink("file:///private/data"))
    }

}
