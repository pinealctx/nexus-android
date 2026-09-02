package com.pinealctx.nexus.core

data class RemotePushPayload(
    val type: Type,
    val senderName: String,
    val groupName: String,
    val message: String?,
    val messageType: Int,
    val conversationId: String?
) {
    enum class Type {
        MESSAGE,
        FRIEND_REQUEST,
        SYNC,
        UNKNOWN
    }

    companion object {
        fun from(data: Map<String, String>): RemotePushPayload {
            val type = when (data["type"]) {
                "message" -> Type.MESSAGE
                "friend_request" -> Type.FRIEND_REQUEST
                "sync" -> Type.SYNC
                else -> Type.UNKNOWN
            }
            return RemotePushPayload(
                type = type,
                senderName = data["sender_name"].orEmpty(),
                groupName = data["group_name"].orEmpty(),
                message = data["message"]?.takeIf { it.isNotBlank() },
                messageType = data["message_type"]?.toIntOrNull() ?: 0,
                conversationId = data["conversation_id"]?.takeIf { it.isNotBlank() }
            )
        }
    }
}
