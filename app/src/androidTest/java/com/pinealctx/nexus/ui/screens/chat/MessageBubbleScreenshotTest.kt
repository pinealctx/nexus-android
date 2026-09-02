package com.pinealctx.nexus.ui.screens.chat

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.pinealctx.nexus.core.MessageContent
import com.pinealctx.nexus.core.MessageData
import com.pinealctx.nexus.ui.theme.NexusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class MessageBubbleScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun textMessageMatchesVisualBaseline() {
        assumeTrue(Build.VERSION.SDK_INT >= 36)
        composeRule.setContent {
            NexusTheme(darkTheme = false) {
                Box(
                    modifier = Modifier
                        .requiredSize(width = 360.dp, height = 120.dp)
                        .testTag(GoldenTag)
                ) {
                    MessageBubble(
                        message = ChatMessageItem.Remote(
                            MessageData(
                                conversationId = "100",
                                messageId = 42,
                                senderId = 7,
                                content = MessageContent.Text("Reliable messages survive restarts."),
                                replyToMessageId = null,
                                replyContext = null,
                                createdAt = 1_700_000_000_000,
                                edited = false,
                                recalled = false
                            )
                        ),
                        currentUserId = 1
                    )
                }
            }
        }

        val bitmap = composeRule.onNodeWithTag(GoldenTag).captureToImage().asAndroidBitmap()

        val actualHash = differenceHash(bitmap)
        val distance = hammingDistance(ExpectedPerceptualHash, actualHash)

        assertTrue(
            "Visual difference exceeded the $MaxHammingDistance-bit tolerance: " +
                "distance=$distance, expected=$ExpectedPerceptualHash, actual=$actualHash",
            distance <= MaxHammingDistance
        )
    }

    private fun differenceHash(source: Bitmap): String {
        val scaled = Bitmap.createScaledBitmap(source, 33, 16, true)
        val bits = buildString(512) {
            for (y in 0 until 16) {
                for (x in 0 until 32) {
                    append(if (luminance(scaled.getPixel(x, y)) > luminance(scaled.getPixel(x + 1, y))) '1' else '0')
                }
            }
        }
        return bits.chunked(4).joinToString("") { nibble ->
            nibble.toInt(radix = 2).toString(radix = 16)
        }
    }

    private fun luminance(color: Int): Int =
        (android.graphics.Color.red(color) * 299 +
            android.graphics.Color.green(color) * 587 +
            android.graphics.Color.blue(color) * 114) / 1000

    private fun hammingDistance(expected: String, actual: String): Int {
        assertEquals("Perceptual hash length", expected.length, actual.length)
        return expected.zip(actual).sumOf { (expectedNibble, actualNibble) ->
            Integer.bitCount(expectedNibble.digitToInt(16) xor actualNibble.digitToInt(16))
        }
    }

    private companion object {
        const val GoldenTag = "message_bubble_golden"
        const val MaxHammingDistance = 8
        const val ExpectedPerceptualHash =
            "00000000000000000000000072c4a000000000008000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
    }
}
