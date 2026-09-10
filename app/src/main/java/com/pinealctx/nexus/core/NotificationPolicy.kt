package com.pinealctx.nexus.core

import com.shared.v1.MessageEnvelope
import com.shared.v1.MessageEntity
import com.shared.v1.MessageType
import com.shared.v1.StreamPhase

object NotificationPolicy {
    fun shouldAlert(message: MessageEnvelope, userId: Int, muted: Boolean): Boolean {
        if (message.senderId == userId || message.senderId == 0 || message.edited) return false
        when (message.body.type) {
            MessageType.MESSAGE_TYPE_RECALLED, MessageType.MESSAGE_TYPE_GROUP -> return false
            MessageType.MESSAGE_TYPE_STREAM -> if (message.body.stream.phase != StreamPhase.STREAM_PHASE_END) return false
            else -> Unit
        }
        val entities: List<MessageEntity> = when (message.body.type) {
            MessageType.MESSAGE_TYPE_TEXT -> message.body.text.entitiesList
            MessageType.MESSAGE_TYPE_MARKDOWN -> message.body.markdown.entitiesList
            else -> emptyList()
        }
        return !muted || entities.any { it.hasMention() && (it.mention.isAll || it.mention.userId == userId) }
    }

    fun plainPreview(text: String): String = text
        .replace(Regex("!?\\[([^]]*)]\\([^)]*\\)"), "$1")
        .replace(Regex("<[^>]*>"), "")
        .replace(Regex("[`*_~#>]+"), "")
        .replace(Regex("\\s+"), " ").trim().take(240)
}
