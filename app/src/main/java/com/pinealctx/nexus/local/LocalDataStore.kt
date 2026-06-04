package com.pinealctx.nexus.local

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.pinealctx.nexus.core.AgentCommandData
import com.pinealctx.nexus.core.AgentInfoData
import com.pinealctx.nexus.core.ConversationData
import com.pinealctx.nexus.core.ContactData
import com.pinealctx.nexus.core.MessageContent
import com.pinealctx.nexus.core.MessageData
import com.pinealctx.nexus.core.MessageReplyContextData
import com.pinealctx.nexus.core.MessageSearchResultData
import com.pinealctx.nexus.core.GroupData
import com.pinealctx.nexus.core.GroupMemberData
import com.pinealctx.nexus.core.LocalMessageData
import com.pinealctx.nexus.core.MediaFileData
import com.pinealctx.nexus.core.PendingRequestData
import com.pinealctx.nexus.core.ProfileData
import com.pinealctx.nexus.core.MessageSendState
import com.pinealctx.nexus.core.previewText
import com.shared.v1.ConversationActionType
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalDataStore @Inject constructor(
    @ApplicationContext context: Context
) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE conversations (
                conversation_id TEXT PRIMARY KEY,
                conversation_type INTEGER NOT NULL,
                peer_id INTEGER NOT NULL,
                display_name TEXT,
                avatar_url TEXT,
                last_message_id INTEGER NOT NULL,
                last_message_time INTEGER NOT NULL,
                last_message_content TEXT,
                is_muted INTEGER NOT NULL,
                last_read_message_id INTEGER NOT NULL,
                deleted INTEGER NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE messages (
                conversation_id TEXT NOT NULL,
                message_id INTEGER NOT NULL,
                sender_id INTEGER NOT NULL,
                content_kind TEXT NOT NULL,
                text TEXT,
                file_id TEXT,
                file_name TEXT,
                file_size INTEGER,
                mime_type TEXT,
                width INTEGER,
                height INTEGER,
                duration INTEGER,
                card_json TEXT,
                fallback_text TEXT,
                reply_to_message_id INTEGER,
                reply_sender_id INTEGER,
                reply_sender_nickname TEXT,
                reply_content_preview TEXT,
                created_at INTEGER NOT NULL,
                edited INTEGER NOT NULL,
                recalled INTEGER NOT NULL,
                PRIMARY KEY (conversation_id, message_id)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE local_messages (
                client_message_id INTEGER PRIMARY KEY,
                conversation_id TEXT NOT NULL,
                server_message_id INTEGER,
                sender_id INTEGER NOT NULL,
                content_kind TEXT NOT NULL,
                text TEXT,
                file_id TEXT,
                file_name TEXT,
                file_size INTEGER,
                mime_type TEXT,
                width INTEGER,
                height INTEGER,
                duration INTEGER,
                card_json TEXT,
                fallback_text TEXT,
                reply_to_message_id INTEGER,
                created_at INTEGER NOT NULL,
                send_state INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE contacts (
                user_id INTEGER PRIMARY KEY,
                username TEXT NOT NULL,
                nickname TEXT NOT NULL,
                avatar_url TEXT NOT NULL,
                alias TEXT,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE my_profile (
                user_id INTEGER PRIMARY KEY,
                username TEXT NOT NULL,
                nickname TEXT NOT NULL,
                avatar_url TEXT NOT NULL,
                signature TEXT NOT NULL,
                phone TEXT,
                email TEXT,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE users_cache (
                user_id INTEGER PRIMARY KEY,
                username TEXT NOT NULL,
                nickname TEXT NOT NULL,
                avatar_url TEXT NOT NULL,
                signature TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE pending_requests (
                request_id INTEGER PRIMARY KEY,
                from_user_id INTEGER NOT NULL,
                to_user_id INTEGER NOT NULL,
                message TEXT,
                status INTEGER NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE agents_cache (
                user_id INTEGER PRIMARY KEY,
                username TEXT NOT NULL,
                nickname TEXT NOT NULL,
                avatar_url TEXT NOT NULL,
                signature TEXT NOT NULL,
                is_system_agent INTEGER NOT NULL,
                mini_app_enabled INTEGER NOT NULL,
                mini_app_url TEXT NOT NULL,
                mini_app_permissions INTEGER NOT NULL,
                commands TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                status INTEGER NOT NULL,
                is_featured INTEGER NOT NULL DEFAULT 0,
                is_mine INTEGER NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE blocked_users (
                user_id INTEGER PRIMARY KEY
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE groups (
                group_id INTEGER PRIMARY KEY,
                name TEXT NOT NULL,
                avatar_url TEXT NOT NULL,
                description TEXT NOT NULL,
                owner_id INTEGER NOT NULL,
                status INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE group_members (
                group_id INTEGER NOT NULL,
                user_id INTEGER NOT NULL,
                role INTEGER NOT NULL,
                joined_at INTEGER NOT NULL,
                display_name TEXT NOT NULL,
                PRIMARY KEY (group_id, user_id)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE media_files (
                file_id TEXT PRIMARY KEY,
                file_name TEXT NOT NULL,
                content_type TEXT NOT NULL,
                size INTEGER NOT NULL,
                width INTEGER NOT NULL,
                height INTEGER NOT NULL,
                duration_ms INTEGER NOT NULL,
                thumbnail_file_id TEXT NOT NULL,
                public_url TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_conversations_sort ON conversations(deleted, last_message_time DESC, last_message_id DESC)")
        db.execSQL("CREATE INDEX idx_messages_sort ON messages(conversation_id, message_id DESC)")
        db.execSQL("CREATE INDEX idx_messages_search ON messages(conversation_id, content_kind, text)")
        db.execSQL("CREATE INDEX idx_local_messages_sort ON local_messages(conversation_id, created_at DESC, client_message_id DESC)")
        db.execSQL("CREATE INDEX idx_local_messages_server_id ON local_messages(conversation_id, server_message_id)")
        db.execSQL("CREATE INDEX idx_users_cache_username ON users_cache(username)")
        db.execSQL("CREATE INDEX idx_agents_featured ON agents_cache(is_featured, nickname COLLATE NOCASE)")
        db.execSQL("CREATE INDEX idx_agents_mine ON agents_cache(is_mine, nickname COLLATE NOCASE)")
        db.execSQL("CREATE INDEX idx_pending_requests_sort ON pending_requests(created_at DESC)")
        db.execSQL("CREATE INDEX idx_group_members_group ON group_members(group_id, role, display_name)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS media_files")
        db.execSQL("DROP TABLE IF EXISTS group_members")
        db.execSQL("DROP TABLE IF EXISTS groups")
        db.execSQL("DROP TABLE IF EXISTS agents_cache")
        db.execSQL("DROP TABLE IF EXISTS blocked_users")
        db.execSQL("DROP TABLE IF EXISTS pending_requests")
        db.execSQL("DROP TABLE IF EXISTS users_cache")
        db.execSQL("DROP TABLE IF EXISTS my_profile")
        db.execSQL("DROP TABLE IF EXISTS contacts")
        db.execSQL("DROP TABLE IF EXISTS local_messages")
        db.execSQL("DROP TABLE IF EXISTS messages")
        db.execSQL("DROP TABLE IF EXISTS conversations")
        onCreate(db)
    }

    fun upsertConversations(conversations: List<ConversationData>) {
        if (conversations.isEmpty()) return
        writableDatabase.transaction {
            conversations.forEach { upsertConversation(it, this) }
        }
    }

    fun upsertConversation(conversation: ConversationData) {
        writableDatabase.transaction {
            upsertConversation(conversation, this)
        }
    }

    fun listConversations(limit: Int = 50, beforeTime: Long? = null): List<ConversationData> {
        val clauses = mutableListOf("deleted = 0")
        val args = mutableListOf<String>()
        if (beforeTime != null) {
            clauses += "last_message_time < ?"
            args += beforeTime.toString()
        }

        val sql = """
            SELECT * FROM conversations
            WHERE ${clauses.joinToString(" AND ")}
            ORDER BY last_message_time DESC, last_message_id DESC
            LIMIT ?
        """.trimIndent()
        args += limit.coerceAtLeast(1).toString()

        return readableDatabase.rawQuery(sql, args.toTypedArray()).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toConversationData())
                }
            }
        }
    }

    fun getConversation(conversationId: Long): ConversationData? {
        return readableDatabase.rawQuery(
            "SELECT * FROM conversations WHERE conversation_id = ? AND deleted = 0",
            arrayOf(conversationId.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toConversationData() else null
        }
    }

    fun upsertContacts(contacts: List<ContactData>) {
        if (contacts.isEmpty()) return
        writableDatabase.transaction {
            contacts.forEach { contact ->
                insertWithOnConflict(
                    "contacts",
                    null,
                    contact.toContentValues(),
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
        }
    }

    fun listContacts(): List<ContactData> {
        return readableDatabase.rawQuery(
            "SELECT * FROM contacts ORDER BY COALESCE(NULLIF(alias, ''), nickname, username) COLLATE NOCASE",
            emptyArray()
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toContactData())
                }
            }
        }
    }

    fun deleteContact(userId: Int) {
        writableDatabase.delete("contacts", "user_id = ?", arrayOf(userId.toString()))
    }

    fun updateContactAlias(userId: Int, alias: String?) {
        val values = ContentValues().apply {
            putNullable("alias", alias)
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.update("contacts", values, "user_id = ?", arrayOf(userId.toString()))
    }

    fun clearContacts() {
        writableDatabase.delete("contacts", null, null)
    }

    fun upsertMyProfile(profile: ProfileData) {
        writableDatabase.transaction {
            delete("my_profile", "user_id != ?", arrayOf(profile.userId.toString()))
            insertWithOnConflict(
                "my_profile",
                null,
                profile.toContentValues(),
                SQLiteDatabase.CONFLICT_REPLACE
            )
            insertWithOnConflict(
                "users_cache",
                null,
                profile.toUserCacheContentValues(),
                SQLiteDatabase.CONFLICT_REPLACE
            )
        }
    }

    fun getMyProfile(): ProfileData? {
        return readableDatabase.rawQuery(
            "SELECT * FROM my_profile LIMIT 1",
            emptyArray()
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toProfileData() else null
        }
    }

    fun upsertUsers(users: List<ContactData>) {
        if (users.isEmpty()) return
        writableDatabase.transaction {
            users.forEach { user ->
                insertWithOnConflict(
                    "users_cache",
                    null,
                    user.toUserCacheContentValues(),
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
        }
    }

    fun upsertUser(user: ContactData) {
        writableDatabase.insertWithOnConflict(
            "users_cache",
            null,
            user.toUserCacheContentValues(),
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun getUser(userId: Int): ContactData? {
        return readableDatabase.rawQuery(
            "SELECT * FROM users_cache WHERE user_id = ?",
            arrayOf(userId.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toUserCacheContactData() else null
        }
    }

    fun getUserByUsername(username: String): ContactData? {
        return readableDatabase.rawQuery(
            "SELECT * FROM users_cache WHERE username = ? COLLATE NOCASE LIMIT 1",
            arrayOf(username)
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toUserCacheContactData() else null
        }
    }

    fun getUsers(userIds: List<Int>): List<ContactData> {
        if (userIds.isEmpty()) return emptyList()
        return userIds.mapNotNull { getUser(it) }
    }

    fun deleteUserCache(userId: Int) {
        writableDatabase.delete("users_cache", "user_id = ?", arrayOf(userId.toString()))
    }

    fun clearUsersCache() {
        writableDatabase.delete("users_cache", null, null)
    }

    fun upsertAgents(
        agents: List<AgentInfoData>,
        featured: Boolean? = null,
        mine: Boolean? = null
    ) {
        if (agents.isEmpty()) return
        writableDatabase.transaction {
            agents.forEach { agent ->
                upsertAgent(agent, featured = featured, mine = mine, db = this)
            }
        }
    }

    fun upsertAgent(
        agent: AgentInfoData,
        featured: Boolean? = null,
        mine: Boolean? = null
    ) {
        writableDatabase.transaction {
            upsertAgent(agent, featured = featured, mine = mine, db = this)
        }
    }

    fun getAgent(agentUserId: Int): AgentInfoData? {
        return readableDatabase.rawQuery(
            "SELECT * FROM agents_cache WHERE user_id = ?",
            arrayOf(agentUserId.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toAgentInfoData() else null
        }
    }

    fun listFeaturedAgents(limit: Int = 50): List<AgentInfoData> =
        listAgentsByFlag("is_featured", limit)

    fun listMyAgents(): List<AgentInfoData> =
        listAgentsByFlag("is_mine", Int.MAX_VALUE)

    fun updateAgentStatus(agentUserId: Int, status: Int) {
        val values = ContentValues().apply {
            put("user_id", agentUserId)
            put("status", status)
            put("updated_at", System.currentTimeMillis())
        }
        val updated = writableDatabase.update("agents_cache", values, "user_id = ?", arrayOf(agentUserId.toString()))
        if (updated == 0) {
            writableDatabase.insertWithOnConflict(
                "agents_cache",
                null,
                ContentValues().apply {
                    put("user_id", agentUserId)
                    put("username", "")
                    put("nickname", "")
                    put("avatar_url", "")
                    put("signature", "")
                    put("is_system_agent", 0)
                    put("mini_app_enabled", 0)
                    put("mini_app_url", "")
                    put("mini_app_permissions", 0)
                    put("commands", "[]")
                    put("created_at", 0L)
                    put("status", status)
                    put("is_featured", 0)
                    put("is_mine", 0)
                    put("updated_at", System.currentTimeMillis())
                },
                SQLiteDatabase.CONFLICT_REPLACE
            )
        }
    }

    fun deleteAgent(agentUserId: Int) {
        writableDatabase.delete("agents_cache", "user_id = ?", arrayOf(agentUserId.toString()))
    }

    private fun listAgentsByFlag(flagColumn: String, limit: Int): List<AgentInfoData> {
        val sql = """
            SELECT * FROM agents_cache
            WHERE $flagColumn = 1
            ORDER BY nickname COLLATE NOCASE, username COLLATE NOCASE, user_id
            LIMIT ?
        """.trimIndent()
        return readableDatabase.rawQuery(sql, arrayOf(limit.coerceAtLeast(1).toString())).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toAgentInfoData())
                }
            }
        }
    }

    private fun upsertAgent(
        agent: AgentInfoData,
        featured: Boolean?,
        mine: Boolean?,
        db: SQLiteDatabase
    ) {
        val existing = db.rawQuery(
            "SELECT is_featured, is_mine FROM agents_cache WHERE user_id = ?",
            arrayOf(agent.userId.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                (cursor.getInt(0) == 1) to (cursor.getInt(1) == 1)
            } else {
                false to false
            }
        }
        db.insertWithOnConflict(
            "agents_cache",
            null,
            agent.toContentValues(
                featured = featured ?: existing.first,
                mine = mine ?: existing.second
            ),
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun upsertPendingRequests(requests: List<PendingRequestData>) {
        if (requests.isEmpty()) return
        writableDatabase.transaction {
            requests.forEach { request ->
                insertWithOnConflict(
                    "pending_requests",
                    null,
                    request.toContentValues(),
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
        }
    }

    fun upsertPendingRequest(request: PendingRequestData) {
        writableDatabase.insertWithOnConflict(
            "pending_requests",
            null,
            request.toContentValues(),
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun listPendingRequests(beforeTime: Long? = null, limit: Int = 20): List<PendingRequestData> {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (beforeTime != null) {
            clauses += "created_at < ?"
            args += beforeTime.toString()
        }
        val where = clauses.takeIf { it.isNotEmpty() }?.joinToString(" AND ")?.let { "WHERE $it" }.orEmpty()
        val sql = """
            SELECT * FROM pending_requests
            $where
            ORDER BY created_at DESC
            LIMIT ?
        """.trimIndent()
        args += limit.coerceAtLeast(1).toString()

        return readableDatabase.rawQuery(sql, args.toTypedArray()).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toPendingRequestData())
                }
            }
        }
    }

    fun removePendingRequest(requestId: Long) {
        writableDatabase.delete("pending_requests", "request_id = ?", arrayOf(requestId.toString()))
    }

    fun clearPendingRequests() {
        writableDatabase.delete("pending_requests", null, null)
    }

    fun replaceBlockedUsers(userIds: List<Int>) {
        writableDatabase.transaction {
            delete("group_members", null, null)
            delete("groups", null, null)
            delete("blocked_users", null, null)
            userIds.forEach { userId ->
                insertWithOnConflict(
                    "blocked_users",
                    null,
                    ContentValues().apply { put("user_id", userId) },
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
        }
    }

    fun setBlockedUser(userId: Int, isBlocked: Boolean) {
        if (isBlocked) {
            writableDatabase.insertWithOnConflict(
                "blocked_users",
                null,
                ContentValues().apply { put("user_id", userId) },
                SQLiteDatabase.CONFLICT_REPLACE
            )
        } else {
            writableDatabase.delete("blocked_users", "user_id = ?", arrayOf(userId.toString()))
        }
    }

    fun listBlockedUsers(): List<Int> {
        return readableDatabase.rawQuery(
            "SELECT user_id FROM blocked_users ORDER BY user_id",
            emptyArray()
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.getInt(0))
                }
            }
        }
    }

    fun upsertGroups(groups: List<GroupData>) {
        if (groups.isEmpty()) return
        writableDatabase.transaction {
            groups.forEach { group ->
                insertWithOnConflict(
                    "groups",
                    null,
                    group.toContentValues(),
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
        }
    }

    fun upsertGroup(group: GroupData) {
        writableDatabase.insertWithOnConflict(
            "groups",
            null,
            group.toContentValues(),
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun listGroups(): List<GroupData> {
        return readableDatabase.rawQuery(
            "SELECT * FROM groups ORDER BY name COLLATE NOCASE, group_id",
            emptyArray()
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toGroupData())
                }
            }
        }
    }

    fun getGroup(groupId: Int): GroupData? {
        return readableDatabase.rawQuery(
            "SELECT * FROM groups WHERE group_id = ?",
            arrayOf(groupId.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toGroupData() else null
        }
    }

    fun deleteGroup(groupId: Int) {
        writableDatabase.transaction {
            delete("group_members", "group_id = ?", arrayOf(groupId.toString()))
            delete("groups", "group_id = ?", arrayOf(groupId.toString()))
        }
    }

    fun updateGroupName(groupId: Int, name: String) {
        updateGroupField(groupId, "name", name)
    }

    fun updateGroupAvatar(groupId: Int, avatarUrl: String) {
        updateGroupField(groupId, "avatar_url", avatarUrl)
    }

    fun updateGroupDescription(groupId: Int, description: String) {
        updateGroupField(groupId, "description", description)
    }

    fun replaceGroupMembers(groupId: Int, members: List<GroupMemberData>) {
        writableDatabase.transaction {
            delete("group_members", "group_id = ?", arrayOf(groupId.toString()))
            members.forEach { member ->
                insertWithOnConflict(
                    "group_members",
                    null,
                    member.toContentValues(groupId),
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
        }
    }

    fun listGroupMembers(groupId: Int): List<GroupMemberData> {
        return readableDatabase.rawQuery(
            """
            SELECT * FROM group_members
            WHERE group_id = ?
            ORDER BY role, display_name COLLATE NOCASE, user_id
            """.trimIndent(),
            arrayOf(groupId.toString())
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toGroupMemberData())
                }
            }
        }
    }

    fun removeGroupMember(groupId: Int, userId: Int) {
        writableDatabase.delete(
            "group_members",
            "group_id = ? AND user_id = ?",
            arrayOf(groupId.toString(), userId.toString())
        )
    }

    fun upsertMediaFile(file: MediaFileData) {
        writableDatabase.insertWithOnConflict(
            "media_files",
            null,
            file.toContentValues(),
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun getMediaFile(fileId: String): MediaFileData? {
        return readableDatabase.rawQuery(
            "SELECT * FROM media_files WHERE file_id = ?",
            arrayOf(fileId)
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toMediaFileData() else null
        }
    }

    fun updateMediaPublicUrl(fileId: String, publicUrl: String) {
        val values = ContentValues().apply {
            put("file_id", fileId)
            put("public_url", publicUrl)
            put("updated_at", System.currentTimeMillis())
        }
        val updated = writableDatabase.update("media_files", values, "file_id = ?", arrayOf(fileId))
        if (updated == 0) {
            upsertMediaFile(
                MediaFileData(
                    fileId = fileId,
                    fileName = "",
                    contentType = "",
                    size = 0L,
                    width = 0,
                    height = 0,
                    durationMs = 0L,
                    thumbnailFileId = "",
                    publicUrl = publicUrl
                )
            )
        }
    }

    fun upsertMessages(messages: List<MessageData>) {
        if (messages.isEmpty()) return
        writableDatabase.transaction {
            messages.forEach { upsertMessage(it, this) }
        }
    }

    fun upsertMessage(message: MessageData) {
        writableDatabase.transaction {
            upsertMessage(message, this)
        }
    }

    fun listMessages(conversationId: Long, limit: Int = 50, beforeId: Long? = null): List<MessageData> {
        val clauses = mutableListOf("conversation_id = ?")
        val args = mutableListOf(conversationId.toString())
        if (beforeId != null) {
            clauses += "message_id < ?"
            args += beforeId.toString()
        }

        val sql = """
            SELECT * FROM messages
            WHERE ${clauses.joinToString(" AND ")}
            ORDER BY message_id DESC
            LIMIT ?
        """.trimIndent()
        args += limit.coerceAtLeast(1).toString()

        return readableDatabase.rawQuery(sql, args.toTypedArray()).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toMessageData())
                }
            }
        }
    }

    fun upsertLocalMessage(message: LocalMessageData) {
        writableDatabase.transaction {
            insertWithOnConflict(
                "local_messages",
                null,
                message.toContentValues(),
                SQLiteDatabase.CONFLICT_REPLACE
            )
            ensureConversation(message.conversationId, this)
        }
    }

    fun listLocalMessages(conversationId: Long): List<LocalMessageData> {
        return readableDatabase.rawQuery(
            """
            SELECT * FROM local_messages
            WHERE conversation_id = ?
            ORDER BY created_at DESC, client_message_id DESC
            """.trimIndent(),
            arrayOf(conversationId.toString())
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toLocalMessageData())
                }
            }
        }
    }

    fun getLocalMessage(clientMessageId: Long): LocalMessageData? {
        return readableDatabase.rawQuery(
            "SELECT * FROM local_messages WHERE client_message_id = ?",
            arrayOf(clientMessageId.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toLocalMessageData() else null
        }
    }

    fun markLocalMessageSending(clientMessageId: Long) {
        val values = ContentValues().apply {
            putNull("server_message_id")
            put("send_state", MessageSendState.SENDING.code)
        }
        writableDatabase.update(
            "local_messages",
            values,
            "client_message_id = ?",
            arrayOf(clientMessageId.toString())
        )
    }

    fun markLocalMessageSent(clientMessageId: Long, serverMessageId: Long) {
        val values = ContentValues().apply {
            put("server_message_id", serverMessageId)
            put("send_state", MessageSendState.SENT.code)
        }
        writableDatabase.update(
            "local_messages",
            values,
            "client_message_id = ?",
            arrayOf(clientMessageId.toString())
        )
    }

    fun markLocalMessageFailed(clientMessageId: Long) {
        val values = ContentValues().apply {
            put("send_state", MessageSendState.FAILED.code)
        }
        writableDatabase.update(
            "local_messages",
            values,
            "client_message_id = ?",
            arrayOf(clientMessageId.toString())
        )
    }

    fun deleteLocalMessage(clientMessageId: Long) {
        writableDatabase.delete(
            "local_messages",
            "client_message_id = ?",
            arrayOf(clientMessageId.toString())
        )
    }

    fun editMessage(conversationId: Long, messageId: Long, text: String) {
        writableDatabase.transaction {
            val values = clearedMessageContentValues("text").apply {
                put("text", text)
                put("edited", 1)
            }
            update(
                "messages",
                values,
                "conversation_id = ? AND message_id = ?",
                arrayOf(conversationId.toString(), messageId.toString())
            )
            refreshConversationPreview(conversationId.toString(), this)
        }
    }

    fun recallMessage(conversationId: Long, messageId: Long) {
        writableDatabase.transaction {
            val values = clearedMessageContentValues("recalled").apply {
                put("recalled", 1)
            }
            update(
                "messages",
                values,
                "conversation_id = ? AND message_id = ?",
                arrayOf(conversationId.toString(), messageId.toString())
            )
            refreshConversationPreview(conversationId.toString(), this)
        }
    }

    fun markConversationRead(conversationId: Long, lastReadMessageId: Long) {
        writableDatabase.transaction {
            ensureConversation(conversationId.toString(), this)
            val values = ContentValues().apply {
                put("last_read_message_id", lastReadMessageId)
                put("updated_at", System.currentTimeMillis())
            }
            update("conversations", values, "conversation_id = ?", arrayOf(conversationId.toString()))
        }
    }

    fun applyConversationAction(
        conversationId: Long,
        action: ConversationActionType,
        clearMessages: Boolean
    ) {
        writableDatabase.transaction {
            ensureConversation(conversationId.toString(), this)
            when (action) {
                ConversationActionType.CONVERSATION_ACTION_TYPE_MUTE ->
                    updateConversationFlags(conversationId, isMuted = true, deleted = false, db = this)
                ConversationActionType.CONVERSATION_ACTION_TYPE_UNMUTE ->
                    updateConversationFlags(conversationId, isMuted = false, deleted = false, db = this)
                ConversationActionType.CONVERSATION_ACTION_TYPE_DELETE -> {
                    updateConversationFlags(conversationId, deleted = true, db = this)
                    if (clearMessages) {
                        delete("messages", "conversation_id = ?", arrayOf(conversationId.toString()))
                        delete("local_messages", "conversation_id = ?", arrayOf(conversationId.toString()))
                    }
                }
                ConversationActionType.CONVERSATION_ACTION_TYPE_UNSPECIFIED,
                ConversationActionType.UNRECOGNIZED -> Unit
            }
        }
    }

    fun deleteMessages(conversationId: Long, messageIds: List<Long>) {
        if (messageIds.isEmpty()) return
        writableDatabase.transaction {
            val placeholders = messageIds.joinToString(",") { "?" }
            val args = arrayOf(conversationId.toString()) + messageIds.map { it.toString() }
            delete("messages", "conversation_id = ? AND message_id IN ($placeholders)", args)
            delete("local_messages", "conversation_id = ? AND server_message_id IN ($placeholders)", args)
            refreshConversationPreview(conversationId.toString(), this)
        }
    }

    fun deleteHistory(conversationId: Long, upToMessageId: Long) {
        if (upToMessageId <= 0L) return
        writableDatabase.transaction {
            delete(
                "messages",
                "conversation_id = ? AND message_id <= ?",
                arrayOf(conversationId.toString(), upToMessageId.toString())
            )
            refreshConversationPreview(conversationId.toString(), this)
        }
    }

    fun searchMessages(
        query: String,
        conversationId: String?,
        limit: Int,
        offset: Int
    ): List<MessageSearchResultData> {
        if (query.isBlank()) return emptyList()

        val clauses = mutableListOf("(text LIKE ? OR fallback_text LIKE ? OR file_name LIKE ?)")
        val like = "%${query.trim()}%"
        val args = mutableListOf(like, like, like)
        if (!conversationId.isNullOrBlank()) {
            clauses += "conversation_id = ?"
            args += conversationId
        }

        val sql = """
            SELECT conversation_id, message_id, sender_id, text, fallback_text, file_name, created_at
            FROM messages
            WHERE ${clauses.joinToString(" AND ")}
            ORDER BY created_at DESC, message_id DESC
            LIMIT ? OFFSET ?
        """.trimIndent()
        args += limit.coerceAtLeast(1).toString()
        args += offset.coerceAtLeast(0).toString()

        return readableDatabase.rawQuery(sql, args.toTypedArray()).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val snippet = cursor.stringOrNull("text")
                        ?: cursor.stringOrNull("fallback_text")
                        ?: cursor.stringOrNull("file_name")
                        ?: ""
                    add(
                        MessageSearchResultData(
                            conversationId = cursor.getString(cursor.getColumnIndexOrThrow("conversation_id")),
                            messageId = cursor.getLong(cursor.getColumnIndexOrThrow("message_id")),
                            senderId = cursor.getInt(cursor.getColumnIndexOrThrow("sender_id")),
                            textSnippet = snippet,
                            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"))
                        )
                    )
                }
            }
        }
    }

    fun messageCount(): Long {
        return readableDatabase.rawQuery("SELECT COUNT(*) FROM messages", emptyArray()).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }
    }

    fun clearAll() {
        writableDatabase.transaction {
            delete("media_files", null, null)
            delete("group_members", null, null)
            delete("groups", null, null)
            delete("agents_cache", null, null)
            delete("blocked_users", null, null)
            delete("pending_requests", null, null)
            delete("users_cache", null, null)
            delete("my_profile", null, null)
            delete("contacts", null, null)
            delete("local_messages", null, null)
            delete("messages", null, null)
            delete("conversations", null, null)
        }
    }

    private fun upsertConversation(conversation: ConversationData, db: SQLiteDatabase) {
        val values = ContentValues().apply {
            put("conversation_id", conversation.conversationId)
            put("conversation_type", conversation.conversationType)
            put("peer_id", conversation.peerId)
            putNullable("display_name", conversation.displayName)
            putNullable("avatar_url", conversation.avatarUrl)
            put("last_message_id", conversation.lastMessageId)
            put("last_message_time", conversation.lastMessageTime)
            putNullable("last_message_content", conversation.lastMessageContent)
            put("is_muted", conversation.isMuted.toInt())
            put("last_read_message_id", conversation.lastReadMessageId)
            put("deleted", 0)
            put("updated_at", System.currentTimeMillis())
        }
        db.insertWithOnConflict("conversations", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun upsertMessage(message: MessageData, db: SQLiteDatabase) {
        db.insertWithOnConflict("messages", null, message.toContentValues(), SQLiteDatabase.CONFLICT_REPLACE)
        db.delete(
            "local_messages",
            "conversation_id = ? AND server_message_id = ?",
            arrayOf(message.conversationId, message.messageId.toString())
        )

        val conversationId = message.conversationId
        val existing = db.rawQuery(
            "SELECT last_message_id FROM conversations WHERE conversation_id = ?",
            arrayOf(conversationId)
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else null
        }

        if (existing == null) {
            insertStubConversation(message, db)
        } else if (message.messageId >= existing) {
            val values = ContentValues().apply {
                put("last_message_id", message.messageId)
                put("last_message_time", message.createdAt)
                put("last_message_content", message.previewText())
                put("deleted", 0)
                put("updated_at", System.currentTimeMillis())
            }
            db.update("conversations", values, "conversation_id = ?", arrayOf(conversationId))
        }
    }

    private fun insertStubConversation(message: MessageData, db: SQLiteDatabase) {
        val values = ContentValues().apply {
            put("conversation_id", message.conversationId)
            put("conversation_type", 0)
            put("peer_id", 0)
            putNull("display_name")
            putNull("avatar_url")
            put("last_message_id", message.messageId)
            put("last_message_time", message.createdAt)
            put("last_message_content", message.previewText())
            put("is_muted", 0)
            put("last_read_message_id", 0L)
            put("deleted", 0)
            put("updated_at", System.currentTimeMillis())
        }
        db.insertWithOnConflict("conversations", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    private fun ensureConversation(conversationId: String, db: SQLiteDatabase) {
        val exists = db.rawQuery(
            "SELECT 1 FROM conversations WHERE conversation_id = ?",
            arrayOf(conversationId)
        ).use { it.moveToFirst() }
        if (exists) return

        val values = ContentValues().apply {
            put("conversation_id", conversationId)
            put("conversation_type", 0)
            put("peer_id", 0)
            putNull("display_name")
            putNull("avatar_url")
            put("last_message_id", 0L)
            put("last_message_time", 0L)
            putNull("last_message_content")
            put("is_muted", 0)
            put("last_read_message_id", 0L)
            put("deleted", 0)
            put("updated_at", System.currentTimeMillis())
        }
        db.insert("conversations", null, values)
    }

    private fun updateConversationFlags(
        conversationId: Long,
        isMuted: Boolean? = null,
        deleted: Boolean? = null,
        db: SQLiteDatabase
    ) {
        val values = ContentValues().apply {
            isMuted?.let { put("is_muted", it.toInt()) }
            deleted?.let { put("deleted", it.toInt()) }
            put("updated_at", System.currentTimeMillis())
        }
        db.update("conversations", values, "conversation_id = ?", arrayOf(conversationId.toString()))
    }

    private fun refreshConversationPreview(conversationId: String, db: SQLiteDatabase) {
        val latest = db.rawQuery(
            """
            SELECT * FROM messages
            WHERE conversation_id = ?
            ORDER BY message_id DESC
            LIMIT 1
            """.trimIndent(),
            arrayOf(conversationId)
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toMessageData() else null
        }

        val values = ContentValues().apply {
            put("last_message_id", latest?.messageId ?: 0L)
            put("last_message_time", latest?.createdAt ?: 0L)
            putNullable("last_message_content", latest?.previewText())
            put("updated_at", System.currentTimeMillis())
        }
        db.update("conversations", values, "conversation_id = ?", arrayOf(conversationId))
    }

    private fun MessageData.toContentValues(): ContentValues {
        return ContentValues().apply {
            put("conversation_id", conversationId)
            put("message_id", messageId)
            put("sender_id", senderId)
            put("content_kind", content.kind)
            putNullable("text", content.textValue)
            putNullable("file_id", content.fileIdValue)
            putNullable("file_name", content.fileNameValue)
            putNullable("file_size", content.fileSizeValue)
            putNullable("mime_type", content.mimeTypeValue)
            putNullable("width", content.widthValue)
            putNullable("height", content.heightValue)
            putNullable("duration", content.durationValue)
            putNullable("card_json", content.cardJsonValue)
            putNullable("fallback_text", content.fallbackValue)
            putNullable("reply_to_message_id", replyToMessageId)
            putNullable("reply_sender_id", replyContext?.senderId)
            putNullable("reply_sender_nickname", replyContext?.senderNickname)
            putNullable("reply_content_preview", replyContext?.contentPreview)
            put("created_at", createdAt)
            put("edited", edited.toInt())
            put("recalled", recalled.toInt())
        }
    }

    private fun LocalMessageData.toContentValues(): ContentValues {
        return ContentValues().apply {
            put("client_message_id", clientMessageId)
            put("conversation_id", conversationId)
            putNullable("server_message_id", serverMessageId)
            put("sender_id", senderId)
            put("content_kind", content.kind)
            putNullable("text", content.textValue)
            putNullable("file_id", content.fileIdValue)
            putNullable("file_name", content.fileNameValue)
            putNullable("file_size", content.fileSizeValue)
            putNullable("mime_type", content.mimeTypeValue)
            putNullable("width", content.widthValue)
            putNullable("height", content.heightValue)
            putNullable("duration", content.durationValue)
            putNullable("card_json", content.cardJsonValue)
            putNullable("fallback_text", content.fallbackValue)
            putNullable("reply_to_message_id", replyToMessageId)
            put("created_at", createdAt)
            put("send_state", sendState.code)
        }
    }

    private fun clearedMessageContentValues(contentKind: String): ContentValues {
        return ContentValues().apply {
            put("content_kind", contentKind)
            putNull("text")
            putNull("file_id")
            putNull("file_name")
            putNull("file_size")
            putNull("mime_type")
            putNull("width")
            putNull("height")
            putNull("duration")
            putNull("card_json")
            putNull("fallback_text")
        }
    }

    private fun ContactData.toContentValues(): ContentValues {
        return ContentValues().apply {
            put("user_id", userId)
            put("username", username)
            put("nickname", nickname)
            put("avatar_url", avatarUrl)
            putNullable("alias", alias)
            put("updated_at", System.currentTimeMillis())
        }
    }

    private fun ProfileData.toContentValues(): ContentValues {
        return ContentValues().apply {
            put("user_id", userId)
            put("username", username)
            put("nickname", nickname)
            put("avatar_url", avatarUrl)
            put("signature", signature)
            putNullable("phone", phone)
            putNullable("email", email)
            put("updated_at", System.currentTimeMillis())
        }
    }

    private fun ProfileData.toUserCacheContentValues(): ContentValues {
        return ContentValues().apply {
            put("user_id", userId)
            put("username", username)
            put("nickname", nickname)
            put("avatar_url", avatarUrl)
            put("signature", signature)
            put("updated_at", System.currentTimeMillis())
        }
    }

    private fun ContactData.toUserCacheContentValues(): ContentValues {
        return ContentValues().apply {
            put("user_id", userId)
            put("username", username)
            put("nickname", nickname)
            put("avatar_url", avatarUrl)
            put("signature", "")
            put("updated_at", System.currentTimeMillis())
        }
    }

    private fun AgentInfoData.toContentValues(featured: Boolean, mine: Boolean): ContentValues {
        return ContentValues().apply {
            put("user_id", userId)
            put("username", username)
            put("nickname", nickname)
            put("avatar_url", avatarUrl)
            put("signature", signature)
            put("is_system_agent", isSystemAgent.toInt())
            put("mini_app_enabled", miniAppEnabled.toInt())
            put("mini_app_url", miniAppUrl)
            put("mini_app_permissions", miniAppPermissions)
            put("commands", commands.toJsonString())
            put("created_at", createdAt)
            put("status", status)
            put("is_featured", featured.toInt())
            put("is_mine", mine.toInt())
            put("updated_at", System.currentTimeMillis())
        }
    }

    private fun PendingRequestData.toContentValues(): ContentValues {
        return ContentValues().apply {
            put("request_id", requestId)
            put("from_user_id", fromUserId)
            put("to_user_id", toUserId)
            putNullable("message", message)
            put("status", status)
            put("created_at", createdAt)
        }
    }

    private fun GroupData.toContentValues(): ContentValues {
        return ContentValues().apply {
            put("group_id", groupId)
            put("name", name)
            put("avatar_url", avatarUrl)
            put("description", description)
            put("owner_id", ownerId)
            put("status", status)
            put("updated_at", System.currentTimeMillis())
        }
    }

    private fun GroupMemberData.toContentValues(groupId: Int): ContentValues {
        return ContentValues().apply {
            put("group_id", groupId)
            put("user_id", userId)
            put("role", role)
            put("joined_at", joinedAt)
            put("display_name", displayName)
        }
    }

    private fun MediaFileData.toContentValues(): ContentValues {
        return ContentValues().apply {
            put("file_id", fileId)
            put("file_name", fileName)
            put("content_type", contentType)
            put("size", size)
            put("width", width)
            put("height", height)
            put("duration_ms", durationMs)
            put("thumbnail_file_id", thumbnailFileId)
            put("public_url", publicUrl)
            put("updated_at", System.currentTimeMillis())
        }
    }

    private fun updateGroupField(groupId: Int, field: String, value: String) {
        val values = ContentValues().apply {
            put(field, value)
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.update("groups", values, "group_id = ?", arrayOf(groupId.toString()))
    }

    private fun Cursor.toConversationData(): ConversationData {
        return ConversationData(
            conversationId = getString(getColumnIndexOrThrow("conversation_id")),
            conversationType = getInt(getColumnIndexOrThrow("conversation_type")),
            peerId = getInt(getColumnIndexOrThrow("peer_id")),
            displayName = stringOrNull("display_name"),
            avatarUrl = stringOrNull("avatar_url"),
            lastMessageId = getLong(getColumnIndexOrThrow("last_message_id")),
            lastMessageTime = getLong(getColumnIndexOrThrow("last_message_time")),
            lastMessageContent = stringOrNull("last_message_content"),
            isMuted = int("is_muted") == 1,
            lastReadMessageId = getLong(getColumnIndexOrThrow("last_read_message_id"))
        )
    }

    private fun Cursor.toContactData(): ContactData {
        return ContactData(
            userId = getInt(getColumnIndexOrThrow("user_id")),
            username = getString(getColumnIndexOrThrow("username")),
            nickname = getString(getColumnIndexOrThrow("nickname")),
            avatarUrl = getString(getColumnIndexOrThrow("avatar_url")),
            alias = stringOrNull("alias")
        )
    }

    private fun Cursor.toProfileData(): ProfileData {
        return ProfileData(
            userId = getInt(getColumnIndexOrThrow("user_id")),
            username = getString(getColumnIndexOrThrow("username")),
            nickname = getString(getColumnIndexOrThrow("nickname")),
            avatarUrl = getString(getColumnIndexOrThrow("avatar_url")),
            signature = getString(getColumnIndexOrThrow("signature")),
            phone = stringOrNull("phone"),
            email = stringOrNull("email")
        )
    }

    private fun Cursor.toUserCacheContactData(): ContactData {
        return ContactData(
            userId = getInt(getColumnIndexOrThrow("user_id")),
            username = getString(getColumnIndexOrThrow("username")),
            nickname = getString(getColumnIndexOrThrow("nickname")),
            avatarUrl = getString(getColumnIndexOrThrow("avatar_url")),
            alias = null
        )
    }

    private fun Cursor.toAgentInfoData(): AgentInfoData {
        return AgentInfoData(
            userId = getInt(getColumnIndexOrThrow("user_id")),
            username = getString(getColumnIndexOrThrow("username")),
            nickname = getString(getColumnIndexOrThrow("nickname")),
            avatarUrl = getString(getColumnIndexOrThrow("avatar_url")),
            signature = getString(getColumnIndexOrThrow("signature")),
            isSystemAgent = int("is_system_agent") == 1,
            miniAppEnabled = int("mini_app_enabled") == 1,
            miniAppUrl = getString(getColumnIndexOrThrow("mini_app_url")),
            miniAppPermissions = getInt(getColumnIndexOrThrow("mini_app_permissions")),
            commands = stringOrNull("commands").orEmpty().toAgentCommands(),
            createdAt = getLong(getColumnIndexOrThrow("created_at")),
            status = getInt(getColumnIndexOrThrow("status"))
        )
    }

    private fun Cursor.toPendingRequestData(): PendingRequestData {
        return PendingRequestData(
            requestId = getLong(getColumnIndexOrThrow("request_id")),
            fromUserId = getInt(getColumnIndexOrThrow("from_user_id")),
            toUserId = getInt(getColumnIndexOrThrow("to_user_id")),
            message = stringOrNull("message"),
            status = getInt(getColumnIndexOrThrow("status")),
            createdAt = getLong(getColumnIndexOrThrow("created_at"))
        )
    }

    private fun Cursor.toGroupData(): GroupData {
        return GroupData(
            groupId = getInt(getColumnIndexOrThrow("group_id")),
            name = getString(getColumnIndexOrThrow("name")),
            avatarUrl = getString(getColumnIndexOrThrow("avatar_url")),
            description = getString(getColumnIndexOrThrow("description")),
            ownerId = getInt(getColumnIndexOrThrow("owner_id")),
            status = getInt(getColumnIndexOrThrow("status"))
        )
    }

    private fun Cursor.toGroupMemberData(): GroupMemberData {
        return GroupMemberData(
            userId = getInt(getColumnIndexOrThrow("user_id")),
            role = getInt(getColumnIndexOrThrow("role")),
            joinedAt = getLong(getColumnIndexOrThrow("joined_at")),
            displayName = getString(getColumnIndexOrThrow("display_name"))
        )
    }

    private fun Cursor.toMediaFileData(): MediaFileData {
        return MediaFileData(
            fileId = getString(getColumnIndexOrThrow("file_id")),
            fileName = getString(getColumnIndexOrThrow("file_name")),
            contentType = getString(getColumnIndexOrThrow("content_type")),
            size = getLong(getColumnIndexOrThrow("size")),
            width = getInt(getColumnIndexOrThrow("width")),
            height = getInt(getColumnIndexOrThrow("height")),
            durationMs = getLong(getColumnIndexOrThrow("duration_ms")),
            thumbnailFileId = getString(getColumnIndexOrThrow("thumbnail_file_id")),
            publicUrl = getString(getColumnIndexOrThrow("public_url"))
        )
    }

    private fun Cursor.toMessageData(): MessageData {
        return MessageData(
            conversationId = getString(getColumnIndexOrThrow("conversation_id")),
            messageId = getLong(getColumnIndexOrThrow("message_id")),
            senderId = getInt(getColumnIndexOrThrow("sender_id")),
            content = toMessageContent(),
            replyToMessageId = longOrNull("reply_to_message_id"),
            replyContext = toMessageReplyContextData(),
            createdAt = getLong(getColumnIndexOrThrow("created_at")),
            edited = int("edited") == 1,
            recalled = int("recalled") == 1
        )
    }

    private fun Cursor.toMessageReplyContextData(): MessageReplyContextData? {
        val messageId = longOrNull("reply_to_message_id") ?: return null
        return MessageReplyContextData(
            messageId = messageId,
            senderId = intOrNull("reply_sender_id") ?: 0,
            senderNickname = stringOrNull("reply_sender_nickname").orEmpty(),
            contentPreview = stringOrNull("reply_content_preview").orEmpty()
        )
    }

    private fun Cursor.toLocalMessageData(): LocalMessageData {
        return LocalMessageData(
            clientMessageId = getLong(getColumnIndexOrThrow("client_message_id")),
            conversationId = getString(getColumnIndexOrThrow("conversation_id")),
            serverMessageId = longOrNull("server_message_id"),
            senderId = getInt(getColumnIndexOrThrow("sender_id")),
            content = toMessageContent(),
            replyToMessageId = longOrNull("reply_to_message_id"),
            createdAt = getLong(getColumnIndexOrThrow("created_at")),
            sendState = MessageSendState.fromCode(getInt(getColumnIndexOrThrow("send_state")))
        )
    }

    private fun Cursor.toMessageContent(): MessageContent {
        return when (stringOrNull("content_kind")) {
            "text" -> MessageContent.Text(stringOrNull("text").orEmpty())
            "image" -> MessageContent.Image(
                fileId = stringOrNull("file_id").orEmpty(),
                width = intOrNull("width") ?: 0,
                height = intOrNull("height") ?: 0
            )
            "audio" -> MessageContent.Audio(
                fileId = stringOrNull("file_id").orEmpty(),
                duration = intOrNull("duration") ?: 0
            )
            "video" -> MessageContent.Video(
                fileId = stringOrNull("file_id").orEmpty(),
                duration = intOrNull("duration") ?: 0,
                width = intOrNull("width") ?: 0,
                height = intOrNull("height") ?: 0
            )
            "file" -> MessageContent.File(
                fileId = stringOrNull("file_id").orEmpty(),
                name = stringOrNull("file_name").orEmpty(),
                size = longOrNull("file_size") ?: 0L,
                mimeType = stringOrNull("mime_type").orEmpty()
            )
            "markdown" -> MessageContent.Markdown(stringOrNull("text").orEmpty())
            "card" -> MessageContent.Card(
                json = stringOrNull("card_json").orEmpty(),
                fallbackText = stringOrNull("fallback_text").orEmpty()
            )
            "recalled" -> MessageContent.Recalled
            else -> MessageContent.Unknown
        }
    }

    private val MessageContent.kind: String
        get() = when (this) {
            is MessageContent.Text -> "text"
            is MessageContent.Image -> "image"
            is MessageContent.Audio -> "audio"
            is MessageContent.Video -> "video"
            is MessageContent.File -> "file"
            is MessageContent.Markdown -> "markdown"
            is MessageContent.Card -> "card"
            MessageContent.Recalled -> "recalled"
            MessageContent.Unknown -> "unknown"
        }

    private val MessageContent.textValue: String?
        get() = when (this) {
            is MessageContent.Text -> text
            is MessageContent.Markdown -> text
            else -> null
        }

    private val MessageContent.fileIdValue: String?
        get() = when (this) {
            is MessageContent.Image -> fileId
            is MessageContent.Audio -> fileId
            is MessageContent.Video -> fileId
            is MessageContent.File -> fileId
            else -> null
        }

    private val MessageContent.fileNameValue: String?
        get() = if (this is MessageContent.File) name else null

    private val MessageContent.fileSizeValue: Long?
        get() = if (this is MessageContent.File) size else null

    private val MessageContent.mimeTypeValue: String?
        get() = if (this is MessageContent.File) mimeType else null

    private val MessageContent.widthValue: Int?
        get() = when (this) {
            is MessageContent.Image -> width
            is MessageContent.Video -> width
            else -> null
        }

    private val MessageContent.heightValue: Int?
        get() = when (this) {
            is MessageContent.Image -> height
            is MessageContent.Video -> height
            else -> null
        }

    private val MessageContent.durationValue: Int?
        get() = when (this) {
            is MessageContent.Audio -> duration
            is MessageContent.Video -> duration
            else -> null
        }

    private val MessageContent.cardJsonValue: String?
        get() = if (this is MessageContent.Card) json else null

    private val MessageContent.fallbackValue: String?
        get() = if (this is MessageContent.Card) fallbackText else null

    private fun List<AgentCommandData>.toJsonString(): String {
        val array = JSONArray()
        forEach { command ->
            array.put(
                JSONObject()
                    .put("command", command.command)
                    .put("description", command.description)
            )
        }
        return array.toString()
    }

    private fun String.toAgentCommands(): List<AgentCommandData> {
        if (isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(this)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        AgentCommandData(
                            command = item.optString("command"),
                            description = item.optString("description")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun Boolean.toInt(): Int = if (this) 1 else 0

    private fun Cursor.int(name: String): Int = getInt(getColumnIndexOrThrow(name))

    private fun Cursor.stringOrNull(name: String): String? {
        val index = getColumnIndexOrThrow(name)
        return if (isNull(index)) null else getString(index)
    }

    private fun Cursor.longOrNull(name: String): Long? {
        val index = getColumnIndexOrThrow(name)
        return if (isNull(index)) null else getLong(index)
    }

    private fun Cursor.intOrNull(name: String): Int? {
        val index = getColumnIndexOrThrow(name)
        return if (isNull(index)) null else getInt(index)
    }

    private fun ContentValues.putNullable(name: String, value: String?) {
        if (value == null) putNull(name) else put(name, value)
    }

    private fun ContentValues.putNullable(name: String, value: Long?) {
        if (value == null) putNull(name) else put(name, value)
    }

    private fun ContentValues.putNullable(name: String, value: Int?) {
        if (value == null) putNull(name) else put(name, value)
    }

    private inline fun SQLiteDatabase.transaction(block: SQLiteDatabase.() -> Unit) {
        beginTransaction()
        try {
            block()
            setTransactionSuccessful()
        } finally {
            endTransaction()
        }
    }

    private companion object {
        const val DATABASE_NAME = "nexus.db"
        const val DATABASE_VERSION = 7
    }
}
