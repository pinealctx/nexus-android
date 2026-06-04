package com.pinealctx.nexus.core

// Auth
data class LoginResult(
    val userId: Int,
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Int,
    val isNewUser: Boolean = false
)

data class ClientConfigData(
    val phoneEnabled: Boolean,
    val emailEnabled: Boolean,
    val wsUrl: String?
)

data class VerifyCodeData(
    val verifyToken: String,
    val expiresIn: Int
)

// Conversations
data class ConversationData(
    val conversationId: String,
    val conversationType: Int,
    val peerId: Int,
    val displayName: String?,
    val avatarUrl: String?,
    val lastMessageId: Long,
    val lastMessageTime: Long,
    val lastMessageContent: String?,
    val isMuted: Boolean,
    val lastReadMessageId: Long
) {
    val unreadCount: Long get() = (lastMessageId - lastReadMessageId).coerceAtLeast(0)
}

// Messages
sealed interface MessageContent {
    data class Text(val text: String) : MessageContent
    data class Image(val fileId: String, val width: Int, val height: Int) : MessageContent
    data class Audio(val fileId: String, val duration: Int) : MessageContent
    data class Video(val fileId: String, val duration: Int, val width: Int = 0, val height: Int = 0) : MessageContent
    data class File(val fileId: String, val name: String, val size: Long, val mimeType: String = "") : MessageContent
    data class Markdown(val text: String) : MessageContent
    data class Card(val json: String, val fallbackText: String = "") : MessageContent
    data object Recalled : MessageContent
    data object Unknown : MessageContent
}

data class MessageData(
    val conversationId: String,
    val messageId: Long,
    val senderId: Int,
    val content: MessageContent,
    val replyToMessageId: Long?,
    val replyContext: MessageReplyContextData?,
    val createdAt: Long,
    val edited: Boolean,
    val recalled: Boolean
)

data class MessageReplyContextData(
    val messageId: Long,
    val senderId: Int,
    val senderNickname: String,
    val contentPreview: String
)

enum class MessageSendState(val code: Int) {
    SENDING(0),
    FAILED(1),
    SENT(2);

    companion object {
        fun fromCode(code: Int): MessageSendState =
            entries.firstOrNull { it.code == code } ?: FAILED
    }
}

data class LocalMessageData(
    val clientMessageId: Long,
    val conversationId: String,
    val serverMessageId: Long?,
    val senderId: Int,
    val content: MessageContent,
    val replyToMessageId: Long?,
    val createdAt: Long,
    val sendState: MessageSendState
)

data class MessageSearchResultData(
    val conversationId: String,
    val messageId: Long,
    val senderId: Int,
    val textSnippet: String,
    val createdAt: Long
)

// Contacts
data class ContactData(
    val userId: Int,
    val username: String,
    val nickname: String,
    val avatarUrl: String,
    val alias: String?
)

data class PendingRequestData(
    val requestId: Long,
    val fromUserId: Int,
    val toUserId: Int,
    val message: String?,
    val status: Int,
    val createdAt: Long
)

// Groups
data class GroupData(
    val groupId: Int,
    val name: String,
    val avatarUrl: String,
    val description: String,
    val ownerId: Int,
    val status: Int
)

data class GroupMemberData(
    val userId: Int,
    val role: Int,
    val joinedAt: Long,
    val displayName: String
)

// User Profile
data class ProfileData(
    val userId: Int,
    val username: String,
    val nickname: String,
    val avatarUrl: String,
    val signature: String,
    val phone: String?,
    val email: String?
)

data class DeviceData(
    val deviceId: String,
    val deviceType: Int,
    val deviceName: String,
    val deviceModel: String,
    val osVersion: String,
    val appVersion: String,
    val loginAt: Long,
    val lastActiveAt: Long,
    val isCurrent: Boolean
)

// Media
data class MediaFileData(
    val fileId: String,
    val fileName: String,
    val contentType: String,
    val size: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val thumbnailFileId: String,
    val publicUrl: String
)

data class UploadSessionData(
    val sessionId: String,
    val uploaded: Long,
    val createdAt: Long
)

// Agents
data class AgentInfoData(
    val userId: Int,
    val username: String,
    val nickname: String,
    val avatarUrl: String,
    val signature: String,
    val isSystemAgent: Boolean,
    val miniAppEnabled: Boolean,
    val miniAppUrl: String,
    val miniAppPermissions: Int,
    val commands: List<AgentCommandData>,
    val createdAt: Long,
    val status: Int = 0
)

data class AgentCommandData(
    val command: String,
    val description: String
)

data class MiniAppLaunchResult(
    val initData: String,
    val miniAppUrl: String
)
