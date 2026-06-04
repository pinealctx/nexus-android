package com.pinealctx.nexus.core

internal fun MessageData.previewText(): String = content.previewText()

internal fun MessageContent.previewText(): String {
    return when (this) {
        is MessageContent.Text -> text
        is MessageContent.Image -> "[Image]"
        is MessageContent.Audio -> "[Audio]"
        is MessageContent.Video -> "[Video]"
        is MessageContent.File -> "[File] $name"
        is MessageContent.Markdown -> text
        is MessageContent.Card -> fallbackText.ifBlank { "[Card]" }
        MessageContent.Recalled -> "[Message recalled]"
        MessageContent.Unknown -> "[Message]"
    }
}
