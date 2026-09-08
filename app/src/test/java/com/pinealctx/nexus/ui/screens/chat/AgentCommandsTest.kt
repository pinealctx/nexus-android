package com.pinealctx.nexus.ui.screens.chat

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.pinealctx.nexus.core.AgentCommandData
import org.junit.Assert.*
import org.junit.Test

class AgentCommandsTest {
    @Test
    fun `normalizes filters and rejects malformed commands`() {
        val commands = listOf(
            AgentCommandData("help", "Show help"), AgentCommandData("/help", "Duplicate"),
            AgentCommandData("/start", "Begin"), AgentCommandData("/", "Invalid"),
            AgentCommandData("/bad command", "Invalid"), AgentCommandData("//bad", "Invalid")
        )
        assertEquals(listOf("/help", "/start"), filteredAgentCommands(commands, "").map { it.command })
        assertEquals("/start", filteredAgentCommands(commands, "BEGIN").single().command)
    }

    @Test
    fun `slash suggestions stop at arguments and preserve cursor semantics`() {
        assertEquals("he", commandQuery(TextFieldValue("/he", TextRange(3))))
        assertNull(commandQuery(TextFieldValue("/he", TextRange(1))))
        assertNull(commandQuery(TextFieldValue("/help arg", TextRange(9))))
        assertNull(commandQuery(TextFieldValue("hello /he", TextRange(9))))
    }

    @Test
    fun `selection preserves existing text and arguments without sending`() {
        val plain = insertAgentCommand(TextFieldValue("draft 😀"), "/help")
        assertEquals("/help draft 😀", plain.text)
        assertEquals(TextRange(6), plain.selection)
        assertEquals("/start argument", insertAgentCommand(TextFieldValue("/help argument"), "/start").text)
        assertEquals("/help ", insertAgentCommand(TextFieldValue("/he"), "/help").text)
    }

    @Test
    fun `long search snippets include the match without splitting emoji`() {
        val text = "😀".repeat(100) + "needle" + "😀".repeat(100)
        val snippet = searchResultSnippet(text, "needle")
        assertTrue(snippet.contains("needle"))
        assertTrue(snippet.startsWith("…"))
        assertTrue(snippet.endsWith("…"))
        assertFalse(snippet[1].isLowSurrogate())
        assertFalse(snippet[snippet.length - 2].isHighSurrogate())
        assertEquals("", searchResultSnippet("", "test"))
    }
}
