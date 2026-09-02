package com.pinealctx.nexus.ui.screens.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.platform.app.InstrumentationRegistry
import com.pinealctx.nexus.R
import com.pinealctx.nexus.core.MessageContent
import com.pinealctx.nexus.core.MessageData
import com.pinealctx.nexus.ui.theme.NexusTheme
import org.junit.Rule
import org.junit.Test

class MessageBubbleTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun ownTextMessageShowsFullActionMenuAndCopiesText() {
        var copiedText: String? = null
        val message = remoteMessage(
            messageId = 10,
            senderId = CurrentUserID,
            content = MessageContent.Text("hello from android")
        )

        composeRule.setContent {
            NexusTheme {
                MessageBubble(
                    message = message,
                    currentUserId = CurrentUserID,
                    onCopy = { copiedText = it }
                )
            }
        }

        composeRule.onNodeWithText("hello from android").performTouchInput { longClick() }

        composeRule.onNodeWithText(text(R.string.chat_action_copy)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.chat_action_reply)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.chat_action_edit)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.chat_action_recall)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.chat_action_delete)).assertIsDisplayed()

        composeRule.onNodeWithText(text(R.string.chat_action_copy)).performClick()

        assert(copiedText == "hello from android")
    }

    @Test
    fun recalledMessageOnlyAllowsDelete() {
        val message = remoteMessage(
            messageId = 11,
            senderId = CurrentUserID,
            content = MessageContent.Text("already gone"),
            recalled = true
        )

        composeRule.setContent {
            NexusTheme {
                MessageBubble(message = message, currentUserId = CurrentUserID)
            }
        }

        composeRule.onNodeWithText(text(R.string.message_recalled)).performTouchInput { longClick() }

        composeRule.onNodeWithText(text(R.string.chat_action_delete)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.chat_action_copy)).assertDoesNotExist()
        composeRule.onNodeWithText(text(R.string.chat_action_reply)).assertDoesNotExist()
        composeRule.onNodeWithText(text(R.string.chat_action_edit)).assertDoesNotExist()
        composeRule.onNodeWithText(text(R.string.chat_action_recall)).assertDoesNotExist()
    }

    @Test
    fun pendingMessageDoesNotOpenActionMenu() {
        val message = remoteMessage(
            messageId = 12,
            senderId = CurrentUserID,
            content = MessageContent.Text("still deleting")
        )

        composeRule.setContent {
            NexusTheme {
                MessageBubble(
                    message = message,
                    currentUserId = CurrentUserID,
                    pendingActionMessageId = 12
                )
            }
        }

        composeRule.onNodeWithText("still deleting").performTouchInput { longClick() }

        composeRule.onNodeWithText(text(R.string.chat_action_deleting)).assertDoesNotExist()
        composeRule.onNodeWithText(text(R.string.chat_action_delete)).assertDoesNotExist()
    }

    @Test
    fun otherUserTextMessageDoesNotShowOwnerActions() {
        val message = remoteMessage(
            messageId = 13,
            senderId = 7,
            content = MessageContent.Text("from another user")
        )

        composeRule.setContent {
            NexusTheme {
                MessageBubble(message = message, currentUserId = CurrentUserID)
            }
        }

        composeRule.onNodeWithText("from another user").performTouchInput { longClick() }

        composeRule.onNodeWithText(text(R.string.chat_action_copy)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.chat_action_reply)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.chat_action_delete)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.chat_action_edit)).assertDoesNotExist()
        composeRule.onNodeWithText(text(R.string.chat_action_recall)).assertDoesNotExist()
    }

    @Test
    fun adaptiveCardActionIsForwardedWithMessageIdentity() {
        var submitted: Triple<Long, String, String>? = null
        val cardJson = """
            {
              "type": "AdaptiveCard",
              "body": [{"type": "TextBlock", "text": "Approval needed"}],
              "actions": [{"type": "Action.Submit", "title": "Approve", "data": {"decision": "yes"}}]
            }
        """.trimIndent()

        composeRule.setContent {
            NexusTheme {
                MessageBubble(
                    message = remoteMessage(20, 7, MessageContent.Card(cardJson, "Approval")),
                    currentUserId = CurrentUserID,
                    onCardAction = { messageId, type, data -> submitted = Triple(messageId, type, data) }
                )
            }
        }

        composeRule.onNodeWithText("Approve").performClick()

        assert(submitted?.first == 20L)
        assert(submitted?.second == "")
        assert(submitted?.third?.contains("decision") == true)
    }

    @Test
    fun adaptiveCardSubmitMergesInputValuesAndUsesActionIdAsVerb() {
        var verb: String? = null
        var payload: String? = null
        val cardJson = """
            {
              "type": "AdaptiveCard",
              "version": "1.5",
              "body": [
                {"type": "Input.Text", "id": "name", "label": "Name", "isRequired": true}
              ],
              "actions": [
                {"type": "Action.Submit", "id": "save-profile", "title": "Save", "data": {"source": "card"}}
              ]
            }
        """.trimIndent()

        composeRule.setContent {
            NexusTheme {
                MessageBubble(
                    message = remoteMessage(22, 7, MessageContent.Card(cardJson, "Profile form")),
                    currentUserId = CurrentUserID,
                    onCardAction = { _, submittedVerb, submittedPayload ->
                        verb = submittedVerb
                        payload = submittedPayload
                    }
                )
            }
        }

        composeRule.onNode(hasSetTextAction()).performTextInput("Ada")
        composeRule.onNodeWithText("Save").performClick()

        assert(verb == "save-profile")
        assert(payload?.contains("\"name\":\"Ada\"") == true)
        assert(payload?.contains("\"source\":\"card\"") == true)
    }

    @Test
    fun adaptiveCardHandlesShowCardAndToggleVisibilityLocally() {
        val cardJson = """
            {
              "type": "AdaptiveCard",
              "version": "1.5",
              "body": [
                {"type": "TextBlock", "id": "secret", "isVisible": false, "text": "Hidden detail"}
              ],
              "actions": [
                {"type": "Action.ToggleVisibility", "title": "Toggle detail", "targetElements": ["secret"]},
                {"type": "Action.ShowCard", "title": "More", "card": {
                  "type": "AdaptiveCard",
                  "body": [{"type": "TextBlock", "text": "Nested card"}]
                }}
              ]
            }
        """.trimIndent()

        composeRule.setContent {
            NexusTheme {
                MessageBubble(
                    message = remoteMessage(23, 7, MessageContent.Card(cardJson, "Details")),
                    currentUserId = CurrentUserID
                )
            }
        }

        composeRule.onNodeWithText("Hidden detail").assertDoesNotExist()
        composeRule.onNodeWithText("Nested card").assertDoesNotExist()
        composeRule.onNodeWithText("Toggle detail").performClick()
        composeRule.onNodeWithText("More").performClick()
        composeRule.onNodeWithText("Hidden detail").assertIsDisplayed()
        composeRule.onNodeWithText("Nested card").assertIsDisplayed()
    }

    @Test
    fun fileBubbleRequestsUrlAndOpensResolvedMedia() {
        var requestedFileId: String? = null
        var openedFileId: String? = null
        composeRule.setContent {
            NexusTheme {
                MessageBubble(
                    message = remoteMessage(
                        21,
                        7,
                        MessageContent.File("file-21", "report.pdf", 4096L, "application/pdf")
                    ),
                    mediaUrls = mapOf("file-21" to "https://example.com/report.pdf"),
                    onMediaNeeded = { requestedFileId = it },
                    onOpenMedia = { openedFileId = it }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("report.pdf").performClick()

        assert(requestedFileId == "file-21")
        assert(openedFileId == "file-21")
    }

    private fun remoteMessage(
        messageId: Long,
        senderId: Int,
        content: MessageContent,
        recalled: Boolean = false
    ): ChatMessageItem.Remote {
        return ChatMessageItem.Remote(
            data = MessageData(
                conversationId = "c1",
                messageId = messageId,
                senderId = senderId,
                content = content,
                replyToMessageId = null,
                replyContext = null,
                createdAt = 1_700_000_000_000,
                edited = false,
                recalled = recalled
            )
        )
    }

    private fun text(resId: Int): String {
        return InstrumentationRegistry.getInstrumentation().targetContext.getString(resId)
    }

    private companion object {
        const val CurrentUserID = 42
    }
}
