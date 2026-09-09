package com.pinealctx.nexus.ui.screens.conversations

import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.pinealctx.nexus.R
import com.pinealctx.nexus.core.*
import com.pinealctx.nexus.ui.theme.NexusTheme
import com.shared.v1.ConversationType
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ConversationListLiveTest {
    @get:Rule val rule = createComposeRule()

    @Test fun reorderedConversationRemainsVisibleAtTop() {
        val a = conversation("100")
        val b = conversation("200")
        var state by mutableStateOf(ConversationListUiState(conversations = listOf(a, b)))
        rule.setContent { NexusTheme { ConversationListContent(state, {}, {}, {}) } }
        rule.runOnIdle { state = state.copy(conversations = listOf(b, a)) }
        rule.waitForIdle()
        rule.onNodeWithText("Chat 200").assertIsDisplayed()
        assertTrue(rule.onNodeWithText("Chat 200").fetchSemanticsNode().boundsInRoot.top <
            rule.onNodeWithText("Chat 100").fetchSemanticsNode().boundsInRoot.top)
    }

    @Test fun sendingFailureAndDraftPreviewAreVisible() {
        val pending = LocalMessageData(1, "100", null, 7, MessageContent.Text("hello"), null, 2, MessageSendState.SENDING)
        var state by mutableStateOf(ConversationListUiState(conversations = listOf(conversation("100").copy(localPreview = pending))))
        var sending = ""
        var failed = ""
        var draft = ""
        rule.setContent {
            sending = stringResource(R.string.conversations_sending, "hello")
            failed = stringResource(R.string.conversations_send_failed, "hello")
            draft = stringResource(R.string.conversations_draft, "unfinished")
            NexusTheme { ConversationListContent(state, {}, {}, {}) }
        }
        rule.onNodeWithText(sending).assertIsDisplayed()
        rule.runOnIdle { state = state.copy(conversations = listOf(conversation("100").copy(localPreview = pending.copy(sendState = MessageSendState.FAILED)))) }
        rule.onNodeWithText(failed).assertIsDisplayed()
        rule.runOnIdle { state = state.copy(conversations = listOf(conversation("100").copy(draft = "unfinished"))) }
        rule.onNodeWithText(draft).assertIsDisplayed()
    }

    @Test fun moreThanFiftyConversationsExposePagination() {
        var calls = 0
        var label = ""
        rule.setContent {
            label = stringResource(R.string.conversations_load_more)
            NexusTheme {
                ConversationListContent(
                    ConversationListUiState(conversations = (1..55).map { conversation(it.toString()) }, hasMore = true),
                    {}, {}, {}, { calls++ }
                )
            }
        }
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(label))
        rule.onNodeWithText(label).performClick()
        rule.runOnIdle { assertEquals(1, calls) }
    }

    private fun conversation(id: String) = ConversationData(
        id, ConversationType.CONVERSATION_TYPE_PRIVATE, 7, "Chat $id", null, 1, 1, "preview", false, 1
    )
}
