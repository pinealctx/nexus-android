package com.pinealctx.nexus.ui.screens.chat

import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.input.TextFieldValue
import com.pinealctx.nexus.R
import com.pinealctx.nexus.core.AgentCommandData
import com.pinealctx.nexus.core.MessageSearchResultData
import com.pinealctx.nexus.ui.theme.NexusTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ChatToolsTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun commandSelectionInsertsDraftWithoutSending() {
        var draft by mutableStateOf(TextFieldValue("draft"))
        var sends = 0
        composeRule.setContent {
            NexusTheme {
                androidx.compose.foundation.layout.Column {
                    AgentCommandList(listOf(AgentCommandData("/help", "Help")), "") {
                        draft = insertAgentCommand(draft, it.command)
                    }
                    ChatComposer(draft, { draft = it }, null, null, null, {}, {}, { sends++ }, {}, {}, {})
                }
            }
        }
        composeRule.onNodeWithText("/help").performClick()
        composeRule.onNodeWithText("/help draft").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, sends) }
    }

    @Test
    fun searchShowsDistinctIdleEmptyAndFailureStates() {
        var state by mutableStateOf(ChatSearchState())
        var retries = 0
        var prompt = ""
        var empty = ""
        var retry = ""
        composeRule.setContent {
            prompt = stringResource(R.string.chat_search_prompt)
            empty = stringResource(R.string.chat_search_empty)
            retry = stringResource(R.string.chat_search_retry)
            NexusTheme { ChatSearchOverlay(state, {}, { retries++ }, {}) }
        }
        composeRule.onNodeWithText(prompt).assertIsDisplayed()
        composeRule.runOnIdle { state = ChatSearchState(query = "test", hasSearched = true) }
        composeRule.onNodeWithText(empty).assertIsDisplayed()
        composeRule.runOnIdle { state = state.copy(failed = true) }
        composeRule.onNodeWithText(empty).assertDoesNotExist()
        composeRule.onNodeWithText(retry).performClick()
        composeRule.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun searchOffersPaginationAndSelectsExactMessage() {
        var loads = 0
        var selected: Long? = null
        var more = ""
        composeRule.setContent {
            more = stringResource(R.string.chat_search_more)
            NexusTheme {
                ChatSearchOverlay(
                    ChatSearchState(query = "test", results = listOf(MessageSearchResultData("100", 42, 7, "test message", 1)), hasMore = true),
                    { loads++ }, {}, { selected = it.messageId }
                )
            }
        }
        composeRule.onNodeWithText(more).performClick()
        composeRule.onNodeWithText("test message").performClick()
        composeRule.runOnIdle { assertEquals(1, loads); assertEquals(42L, selected) }
    }
}
