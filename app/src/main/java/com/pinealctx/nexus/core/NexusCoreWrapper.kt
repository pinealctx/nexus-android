package com.pinealctx.nexus.core

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import uniffi.nexus_ffi.CoreConfig
import uniffi.nexus_ffi.DeviceInfo
import uniffi.nexus_ffi.NexusClient
import uniffi.nexus_ffi.NexusException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NexusCoreWrapper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secureStorage: SecureStorage
) {
    private var client: NexusClient? = null

    fun initialize() {
        if (client != null) return

        System.loadLibrary("nexus_ffi")

        val dbPath = context.getDatabasePath("nexus.db").absolutePath
        context.getDatabasePath("nexus.db").parentFile?.mkdirs()

        val config = CoreConfig(
            databasePath = dbPath,
            apiBaseUrl = "https://api.nexus-dev.xsyphon.com",
            wsUrl = "wss://api.nexus-dev.xsyphon.com/ws",
            deviceId = secureStorage.getDeviceId(),
            deviceInfo = DeviceInfo(
                deviceName = Build.DEVICE,
                deviceModel = Build.MODEL,
                osVersion = "Android ${Build.VERSION.RELEASE}",
                appVersion = "0.1.0"
            )
        )

        client = NexusClient(config)
    }

    fun shutdown() {
        client?.close()
        client = null
    }

    private fun requireClient(): NexusClient =
        client ?: throw IllegalStateException("Core not initialized")

    // ═══════════════════════════════════════════
    // Auth
    // ═══════════════════════════════════════════

    fun getClientConfig(): ClientConfigData {
        val config = client?.getClientConfig() ?: return ClientConfigData(phoneEnabled = true, emailEnabled = false)
        return ClientConfigData(phoneEnabled = config.phoneEnabled, emailEnabled = config.emailEnabled)
    }

    fun requestVerifyCode(identityType: Int, identityValue: String): VerifyCodeData {
        val result = requireClient().requestVerifyCode(identityType, identityValue)
        return VerifyCodeData(verifyToken = result.verifyToken, expiresIn = result.expiresIn)
    }

    fun verifyCode(verifyToken: String, code: String): LoginResult {
        val result = requireClient().verifyCode(verifyToken, code)
        return LoginResult(
            userId = result.userId,
            accessToken = result.accessToken,
            refreshToken = result.refreshToken,
            expiresIn = result.expiresIn,
            isNewUser = result.isNewUser
        )
    }

    fun restoreSession(accessToken: String, refreshToken: String, expiresIn: Int, userId: Int) {
        client?.restoreSession(accessToken, refreshToken, expiresIn, userId)
    }

    fun isAuthenticated(): Boolean = client?.isAuthenticated() ?: false

    fun logout() { client?.logout() }

    fun logoutAll() { client?.logoutAll() }

    fun setupPassword(password: String) { requireClient().setupPassword(password) }

    fun changePassword(oldPassword: String, newPassword: String) {
        requireClient().changePassword(oldPassword, newPassword)
    }

    // ═══════════════════════════════════════════
    // Sync
    // ═══════════════════════════════════════════

    fun coldStart(): Long = requireClient().coldStart()

    fun getLocalSn(): Long = client?.getLocalSn() ?: 0

    fun clearLocalData() { client?.clearLocalData() }

    fun startSync() { client?.startSync() }

    fun stopSync() { client?.stopSync() }

    // ═══════════════════════════════════════════
    // Conversations
    // ═══════════════════════════════════════════

    fun getConversations(limit: Int = 50, beforeTime: Long? = null): List<ConversationData> {
        val conversations = client?.getConversations(limit, beforeTime) ?: return emptyList()
        return conversations.map { it.toData() }
    }

    fun fetchConversations(limit: Int = 50, beforeTime: Long? = null): List<ConversationData> {
        val conversations = client?.fetchConversations(limit, beforeTime) ?: return emptyList()
        return conversations.map { it.toData() }
    }

    fun getConversation(conversationId: Long): ConversationData? {
        return client?.getConversation(conversationId)?.toData()
    }

    fun markAsRead(conversationId: Long, upToMessageId: Long) {
        client?.markAsRead(conversationId, upToMessageId)
    }

    fun muteConversation(conversationId: Long) { client?.muteConversation(conversationId) }

    fun unmuteConversation(conversationId: Long) { client?.unmuteConversation(conversationId) }

    fun deleteConversation(conversationId: Long, clearMessages: Boolean) {
        client?.deleteConversation(conversationId, clearMessages)
    }

    // ═══════════════════════════════════════════
    // Messages
    // ═══════════════════════════════════════════

    fun getMessages(conversationId: String, limit: Int = 50, beforeId: Long? = null): List<MessageData> {
        val messages = client?.getMessages(conversationId, limit, beforeId) ?: return emptyList()
        return messages.map { m ->
            MessageData(
                conversationId = m.conversationId,
                messageId = m.messageId,
                senderId = m.senderId,
                content = m.content,
                replyToMessageId = m.replyToMessageId,
                createdAt = m.createdAt,
                edited = m.edited,
                recalled = m.recalled
            )
        }
    }

    fun sendMessage(conversationId: Long, text: String): Long =
        requireClient().sendMessage(conversationId, text)

    fun sendReplyMessage(conversationId: Long, text: String, replyToMessageId: Long): Long =
        requireClient().sendReplyMessage(conversationId, text, replyToMessageId)

    fun sendImageMessage(conversationId: Long, fileId: String, width: Int, height: Int): Long =
        requireClient().sendImageMessage(conversationId, fileId, width, height)

    fun sendFileMessage(conversationId: Long, fileId: String, name: String, size: Long): Long =
        requireClient().sendFileMessage(conversationId, fileId, name, size)

    fun sendAudioMessage(conversationId: Long, fileId: String, durationMs: Int): Long =
        requireClient().sendAudioMessage(conversationId, fileId, durationMs)

    fun sendVideoMessage(conversationId: Long, fileId: String, width: Int, height: Int, durationMs: Int): Long =
        requireClient().sendVideoMessage(conversationId, fileId, width, height, durationMs)

    fun sendMarkdownMessage(conversationId: Long, rawMarkdown: String): Long =
        requireClient().sendMarkdownMessage(conversationId, rawMarkdown)

    fun sendCardMessage(conversationId: Long, cardJson: String, fallbackText: String): Long =
        requireClient().sendCardMessage(conversationId, cardJson, fallbackText)

    fun editMessage(conversationId: Long, messageId: Long, text: String) {
        requireClient().editMessage(conversationId, messageId, text)
    }

    fun recallMessage(conversationId: Long, messageId: Long) {
        client?.recallMessage(conversationId, messageId)
    }

    fun deleteMessages(conversationId: Long, messageIds: List<Long>) {
        client?.deleteMessages(conversationId, messageIds)
    }

    fun deleteHistory(conversationId: Long, upToMessageId: Long) {
        client?.deleteHistory(conversationId, upToMessageId)
    }

    fun submitCardAction(conversationId: Long, messageId: Long, actionData: String, verb: String? = null): String =
        requireClient().submitCardAction(conversationId, messageId, actionData, verb)

    // ═══════════════════════════════════════════
    // Contacts
    // ═══════════════════════════════════════════

    fun getContacts(): List<ContactData> {
        val contacts = client?.getContacts() ?: return emptyList()
        return contacts.map { it.toContactData() }
    }

    fun fetchContacts() { client?.fetchContacts() }

    fun deleteContact(userId: Int) { client?.deleteContact(userId) }

    fun addContact(targetUserId: Int) { client?.addContact(targetUserId) }

    fun updateContactAlias(contactUserId: Int, alias: String?) {
        client?.updateContactAlias(contactUserId, alias)
    }

    fun searchUsers(query: String): List<ContactData> {
        val results = client?.searchUsers(query) ?: return emptyList()
        return results.map { it.toContactData() }
    }

    // ═══════════════════════════════════════════
    // Friend Requests
    // ═══════════════════════════════════════════

    fun sendFriendRequest(targetUserId: Int, message: String) {
        client?.sendFriendRequest(targetUserId, message)
    }

    fun acceptFriendRequest(requestId: Long) { client?.acceptFriendRequest(requestId) }

    fun rejectFriendRequest(requestId: Long) { client?.rejectFriendRequest(requestId) }

    fun getPendingRequests(): List<PendingRequestData> {
        val requests = client?.getPendingRequests() ?: return emptyList()
        return requests.map { r ->
            PendingRequestData(r.requestId, r.fromUserId, r.toUserId, r.message, r.status, r.createdAt)
        }
    }

    fun listPendingRequests(beforeTime: Long? = null, limit: Int = 20): List<PendingRequestData> {
        val requests = client?.listPendingRequests(beforeTime, limit) ?: return emptyList()
        return requests.map { r ->
            PendingRequestData(r.requestId, r.fromUserId, r.toUserId, r.message, r.status, r.createdAt)
        }
    }

    // ═══════════════════════════════════════════
    // Groups
    // ═══════════════════════════════════════════

    fun listGroups(): List<GroupData> {
        val groups = client?.listGroups() ?: return emptyList()
        return groups.map { g ->
            GroupData(g.groupId, g.name, g.avatarUrl, g.description, g.ownerId, g.status)
        }
    }

    fun fetchGroups() { client?.fetchGroups() }

    fun getGroupInfo(groupId: Int) { client?.getGroupInfo(groupId) }

    fun getGroupMembers(groupId: Int): List<GroupMemberData> {
        val members = client?.getGroupMembers(groupId) ?: return emptyList()
        return members.map { m ->
            GroupMemberData(m.userId, m.role, m.joinedAt, m.displayName)
        }
    }

    fun createGroup(name: String, memberIds: List<Int>): Int =
        requireClient().createGroup(name, memberIds)

    fun dissolveGroup(groupId: Int) { client?.dissolveGroup(groupId) }

    fun leaveGroup(groupId: Int) { client?.leaveGroup(groupId) }

    fun updateGroupName(groupId: Int, name: String) { client?.updateGroupName(groupId, name) }

    fun updateGroupAvatar(groupId: Int, avatarUrl: String) { client?.updateGroupAvatar(groupId, avatarUrl) }

    fun updateGroupDescription(groupId: Int, description: String) { client?.updateGroupDescription(groupId, description) }

    fun inviteMembers(groupId: Int, memberIds: List<Int>) { client?.inviteMembers(groupId, memberIds) }

    fun removeMember(groupId: Int, targetId: Int) { client?.removeMember(groupId, targetId) }

    // ═══════════════════════════════════════════
    // User Profile
    // ═══════════════════════════════════════════

    fun getMyProfile(): ProfileData? {
        val p = client?.getMyProfile() ?: return null
        return ProfileData(p.userId, p.username, p.nickname, p.avatarUrl, p.signature, p.phone, p.email)
    }

    fun fetchProfile() { client?.fetchProfile() }

    fun updateProfile(nickname: String? = null, signature: String? = null, avatarUrl: String? = null) {
        client?.updateProfile(nickname, signature, avatarUrl)
    }

    fun setUsername(username: String) { requireClient().setUsername(username) }

    fun resolveUsername(username: String): ContactData? {
        val result = client?.resolveUsername(username) ?: return null
        return result.toContactData()
    }

    fun batchGetUserInfo(userIds: List<Int>): List<ContactData> {
        val results = client?.batchGetUserInfo(userIds) ?: return emptyList()
        return results.map { it.toContactData() }
    }

    fun listDevices(): List<DeviceData> {
        val devices = client?.listDevices() ?: return emptyList()
        return devices.map { d ->
            DeviceData(d.deviceId, d.deviceType, d.deviceName, d.deviceModel, d.osVersion, d.appVersion, d.loginAt, d.lastActiveAt, d.isCurrent)
        }
    }

    fun removeDevice(deviceId: String) { client?.removeDevice(deviceId) }

    // ═══════════════════════════════════════════
    // Block
    // ═══════════════════════════════════════════

    fun blockUser(userId: Int) { client?.blockUser(userId) }

    fun unblockUser(userId: Int) { client?.unblockUser(userId) }

    fun getBlockedUsers(): List<Int> = client?.getBlockedUsers() ?: emptyList()

    // ═══════════════════════════════════════════
    // Media
    // ═══════════════════════════════════════════

    fun getMediaUrl(fileId: String): String = client?.getMediaUrl(fileId) ?: ""

    fun getDownloadUrl(fileId: String): String = client?.getDownloadUrl(fileId) ?: ""

    fun uploadFile(data: ByteArray, fileName: String, contentType: String, purpose: Int): MediaFileData {
        val result = requireClient().uploadFile(data, fileName, contentType, purpose)
        return result.toMediaFileData()
    }

    fun initUpload(fileName: String, contentType: String, size: Long): UploadSessionData {
        val result = requireClient().initUpload(fileName, contentType, size)
        return UploadSessionData(result.sessionId, result.uploaded, result.createdAt)
    }

    fun uploadChunk(sessionId: String, chunk: ByteArray, offset: Long) {
        requireClient().uploadChunk(sessionId, chunk, offset)
    }

    fun completeUpload(sessionId: String): MediaFileData {
        val result = requireClient().completeUpload(sessionId)
        return result.toMediaFileData()
    }

    // ═══════════════════════════════════════════
    // Push
    // ═══════════════════════════════════════════

    fun registerPushToken(token: String, platform: Int) {
        client?.registerPushToken(token, platform)
    }

    fun unregisterPushToken() { client?.unregisterPushToken() }

    fun clearBadge() { client?.clearBadge() }

    // ═══════════════════════════════════════════
    // Agent
    // ═══════════════════════════════════════════

    fun listFeaturedAgents(limit: Int = 50): List<AgentInfoData> {
        val agents = client?.listFeaturedAgents(limit) ?: return emptyList()
        return agents.map { it.toAgentInfoData() }
    }

    fun getAgentInfo(agentUserId: Int): AgentInfoData? {
        return client?.getAgentInfo(agentUserId)?.toAgentInfoData()
    }

    fun getMiniAppLaunchData(agentUserId: Int, conversationId: Long, startParam: String = ""): MiniAppLaunchResult {
        val result = requireClient().getMiniAppLaunchData(agentUserId, conversationId, startParam, "android")
        return MiniAppLaunchResult(result.initData, result.miniAppUrl)
    }

    fun createAgent(username: String, nickname: String, description: String): Int =
        requireClient().createAgent(username, nickname, description)

    fun listMyAgents(): List<AgentInfoData> {
        val agents = client?.listMyAgents() ?: return emptyList()
        return agents.map { it.toAgentInfoData() }
    }
}

// ═══════════════════════════════════════════
// Extension helpers
// ═══════════════════════════════════════════

private fun uniffi.nexus_ffi.ConversationInfo.toData() = ConversationData(
    conversationId, conversationType, peerId, lastMessageTime, lastMessagePreview, isMuted, unreadCount
)

private fun uniffi.nexus_ffi.ContactInfo.toContactData() = ContactData(
    userId, username, nickname, avatarUrl, alias
)

private fun uniffi.nexus_ffi.MediaFileInfoFfi.toMediaFileData() = MediaFileData(
    fileId, fileName, contentType, size, width, height, durationMs, thumbnailFileId, publicUrl
)

private fun uniffi.nexus_ffi.AgentInfoFfi.toAgentInfoData() = AgentInfoData(
    userId = userId,
    username = username,
    nickname = nickname,
    avatarUrl = avatarUrl,
    signature = signature,
    isSystemAgent = isSystemAgent,
    miniAppEnabled = miniAppEnabled,
    miniAppUrl = miniAppUrl,
    miniAppPermissions = miniAppPermissions,
    commands = commands.map { AgentCommandData(it.command, it.description) },
    createdAt = createdAt
)

// ═══════════════════════════════════════════
// Data classes
// ═══════════════════════════════════════════

data class LoginResult(val userId: Int, val accessToken: String, val refreshToken: String, val expiresIn: Int, val isNewUser: Boolean = false)
data class ClientConfigData(val phoneEnabled: Boolean, val emailEnabled: Boolean)
data class VerifyCodeData(val verifyToken: String, val expiresIn: Int)
data class ConversationData(val conversationId: String, val conversationType: Int, val peerId: Int, val lastMessageTime: Long, val lastMessagePreview: String?, val isMuted: Boolean, val unreadCount: Long)
data class MessageData(val conversationId: String, val messageId: Long, val senderId: Int, val content: uniffi.nexus_ffi.MessageContent, val replyToMessageId: Long?, val createdAt: Long, val edited: Boolean, val recalled: Boolean)
data class ContactData(val userId: Int, val username: String, val nickname: String, val avatarUrl: String, val alias: String?)
data class PendingRequestData(val requestId: Long, val fromUserId: Int, val toUserId: Int, val message: String?, val status: Int, val createdAt: Long)
data class GroupData(val groupId: Int, val name: String, val avatarUrl: String, val description: String, val ownerId: Int, val status: Int)
data class GroupMemberData(val userId: Int, val role: Int, val joinedAt: Long, val displayName: String)
data class ProfileData(val userId: Int, val username: String, val nickname: String, val avatarUrl: String, val signature: String, val phone: String?, val email: String?)
data class DeviceData(val deviceId: String, val deviceType: Int, val deviceName: String, val deviceModel: String, val osVersion: String, val appVersion: String, val loginAt: Long, val lastActiveAt: Long, val isCurrent: Boolean)
data class MediaFileData(val fileId: String, val fileName: String, val contentType: String, val size: Long, val width: Int, val height: Int, val durationMs: Long, val thumbnailFileId: String, val publicUrl: String)
data class UploadSessionData(val sessionId: String, val uploaded: Long, val createdAt: Long)
data class AgentInfoData(val userId: Int, val username: String, val nickname: String, val avatarUrl: String, val signature: String, val isSystemAgent: Boolean, val miniAppEnabled: Boolean, val miniAppUrl: String, val miniAppPermissions: Int, val commands: List<AgentCommandData>, val createdAt: Long)
data class AgentCommandData(val command: String, val description: String)
data class MiniAppLaunchResult(val initData: String, val miniAppUrl: String)
