package com.pinealctx.nexus.ui.screens.chat

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import com.pinealctx.nexus.core.MessageContent
import com.pinealctx.nexus.core.MessageData
import com.pinealctx.nexus.core.TextEntityData
import com.pinealctx.nexus.ui.theme.NexusTheme
import com.shared.v1.MessageEntityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class RichTextMessageTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun mentionInsideBubbleOpensExactUser() {
        var selected: Int? = null
        composeRule.setContent {
            NexusTheme {
                MessageBubble(
                    message = remote(MessageContent.Text("@Alice", listOf(TextEntityData(
                        MessageEntityType.MESSAGE_ENTITY_TYPE_MENTION, 0, 6, userId = 73
                    )))),
                    onMentionClick = { selected = it }
                )
            }
        }
        composeRule.onNodeWithText("@Alice").performTouchInput { click(center) }
        composeRule.runOnIdle { assertEquals(73, selected) }
    }

    @Test
    fun webLinkOpensSystemHandler() {
        var opened: String? = null
        composeRule.setContent {
            CompositionLocalProvider(LocalUriHandler provides object : UriHandler {
                override fun openUri(uri: String) { opened = uri }
            }) {
                NexusTheme { MessageBubble(message = remote(MessageContent.Text("https://example.com"))) }
            }
        }
        composeRule.onNodeWithText("https://example.com").performTouchInput { click(center) }
        composeRule.runOnIdle { assertEquals("https://example.com", opened) }
    }

    @Test
    fun unsupportedLinkSchemeStaysReadableWithoutOpening() {
        var opened: String? = null
        composeRule.setContent {
            CompositionLocalProvider(LocalUriHandler provides object : UriHandler {
                override fun openUri(uri: String) { opened = uri }
            }) {
                NexusTheme { MessageBubble(message = remote(MessageContent.Text("Open", listOf(TextEntityData(
                    MessageEntityType.MESSAGE_ENTITY_TYPE_URL, 0, 4, value = "javascript:alert(1)"
                ))))) }
            }
        }
        composeRule.onNodeWithText("Open").assertIsDisplayed().performTouchInput { click(center) }
        composeRule.runOnIdle { assertNull(opened) }
    }

    @Test
    fun suggestionsFilterAndSelectMembers() {
        var selected: Int? = null
        composeRule.setContent {
            NexusTheme {
                MentionSuggestions(
                    candidates = listOf(MentionCandidate(7, "Alice"), MentionCandidate(8, "Bob")),
                    query = MentionQuery(0, 3, "al"), loading = false, failed = false,
                    onRetry = {}, onSelect = { selected = it.userId }
                )
            }
        }
        composeRule.onNodeWithText("@Bob").assertDoesNotExist()
        composeRule.onNodeWithText("@Alice").performClick()
        composeRule.runOnIdle { assertEquals(7, selected) }
    }

    private fun remote(content: MessageContent) = ChatMessageItem.Remote(MessageData(
        conversationId = "100", messageId = 1, senderId = 7, content = content,
        replyToMessageId = null, replyContext = null, createdAt = 1_700_000_000_000,
        edited = false, recalled = false
    ))
}
