package com.pinealctx.nexus.ui.screens.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
