package com.pinealctx.nexus.local

import androidx.room.ColumnInfo
import androidx.room.Database
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.RoomDatabase
import androidx.room.Query
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        LocalMessageEntity::class,
        ContactEntity::class,
        MyProfileEntity::class,
        UserCacheEntity::class,
        PendingRequestEntity::class,
        AgentCacheEntity::class,
        BlockedUserEntity::class,
        GroupEntity::class,
        GroupMemberEntity::class,
        MediaFileEntity::class
    ],
    version = 9,
    exportSchema = true
)
abstract class NexusDatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao

    companion object {
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN thumbnail_file_id TEXT")
                db.execSQL("ALTER TABLE messages ADD COLUMN transcript TEXT")
                db.execSQL("ALTER TABLE local_messages ADD COLUMN thumbnail_file_id TEXT")
                db.execSQL("ALTER TABLE local_messages ADD COLUMN transcript TEXT")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN stream_phase INTEGER")
                db.execSQL("ALTER TABLE messages ADD COLUMN stream_seq INTEGER")
                db.execSQL("ALTER TABLE messages ADD COLUMN stream_content_type TEXT")
                db.execSQL("ALTER TABLE messages ADD COLUMN stream_error_message TEXT")
            }
        }
    }
}

@Dao
interface CacheDao {
    @Query(
        """
        SELECT * FROM conversations
        WHERE deleted = 0 AND (:beforeTime IS NULL OR last_message_time < :beforeTime)
        ORDER BY last_message_time DESC, last_message_id DESC
        LIMIT :limit
        """
    )
    fun observeConversations(limit: Int, beforeTime: Long?): Flow<List<ConversationEntity>>

    @Query(
        """
        SELECT * FROM messages
        WHERE conversation_id = :conversationId
        ORDER BY message_id DESC
        LIMIT :limit
        """
    )
    fun observeMessages(conversationId: String, limit: Int): Flow<List<MessageEntity>>

    @Query(
        """
        SELECT * FROM local_messages
        WHERE conversation_id = :conversationId
        ORDER BY created_at DESC, client_message_id DESC
        """
    )
    fun observeLocalMessages(conversationId: String): Flow<List<LocalMessageEntity>>

    @Query(
        """
        SELECT * FROM contacts
        ORDER BY COALESCE(NULLIF(alias, ''), nickname, username) COLLATE NOCASE
        """
    )
    fun observeContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM groups ORDER BY name COLLATE NOCASE, group_id")
    fun observeGroups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM pending_requests ORDER BY created_at DESC")
    fun observePendingRequests(): Flow<List<PendingRequestEntity>>
}

@Entity(
    tableName = "conversations",
    indices = [
        Index(
            name = "idx_conversations_sort",
            value = ["deleted", "last_message_time", "last_message_id"]
        )
    ]
)
data class ConversationEntity(
    @PrimaryKey @ColumnInfo(name = "conversation_id") val conversationId: String,
    @ColumnInfo(name = "conversation_type") val conversationType: Int,
    @ColumnInfo(name = "peer_id") val peerId: Int,
    @ColumnInfo(name = "display_name") val displayName: String?,
    @ColumnInfo(name = "avatar_url") val avatarUrl: String?,
    @ColumnInfo(name = "last_message_id") val lastMessageId: Long,
    @ColumnInfo(name = "last_message_time") val lastMessageTime: Long,
    @ColumnInfo(name = "last_message_content") val lastMessageContent: String?,
    @ColumnInfo(name = "is_muted") val isMuted: Int,
    @ColumnInfo(name = "last_read_message_id") val lastReadMessageId: Long,
    @ColumnInfo(name = "deleted", defaultValue = "0") val deleted: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(
    tableName = "messages",
    primaryKeys = ["conversation_id", "message_id"],
    indices = [
        Index(name = "idx_messages_sort", value = ["conversation_id", "message_id"]),
        Index(name = "idx_messages_search", value = ["conversation_id", "content_kind", "text"])
    ]
)
data class MessageEntity(
    @ColumnInfo(name = "conversation_id") val conversationId: String,
    @ColumnInfo(name = "message_id") val messageId: Long,
    @ColumnInfo(name = "sender_id") val senderId: Int,
    @ColumnInfo(name = "content_kind") val contentKind: String,
    val text: String?,
    @ColumnInfo(name = "file_id") val fileId: String?,
    @ColumnInfo(name = "thumbnail_file_id") val thumbnailFileId: String?,
    val transcript: String?,
    @ColumnInfo(name = "file_name") val fileName: String?,
    @ColumnInfo(name = "file_size") val fileSize: Long?,
    @ColumnInfo(name = "mime_type") val mimeType: String?,
    val width: Int?,
    val height: Int?,
    val duration: Int?,
    @ColumnInfo(name = "card_json") val cardJson: String?,
    @ColumnInfo(name = "fallback_text") val fallbackText: String?,
    @ColumnInfo(name = "stream_phase") val streamPhase: Int?,
    @ColumnInfo(name = "stream_seq") val streamSequence: Int?,
    @ColumnInfo(name = "stream_content_type") val streamContentType: String?,
    @ColumnInfo(name = "stream_error_message") val streamErrorMessage: String?,
    @ColumnInfo(name = "reply_to_message_id") val replyToMessageId: Long?,
    @ColumnInfo(name = "reply_sender_id") val replySenderId: Int?,
    @ColumnInfo(name = "reply_sender_nickname") val replySenderNickname: String?,
    @ColumnInfo(name = "reply_content_preview") val replyContentPreview: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    val edited: Int,
    val recalled: Int
)

@Entity(
    tableName = "local_messages",
    indices = [
        Index(
            name = "idx_local_messages_sort",
            value = ["conversation_id", "created_at", "client_message_id"]
        ),
        Index(
            name = "idx_local_messages_server_id",
            value = ["conversation_id", "server_message_id"]
        )
    ]
)
data class LocalMessageEntity(
    @PrimaryKey @ColumnInfo(name = "client_message_id") val clientMessageId: Long,
    @ColumnInfo(name = "conversation_id") val conversationId: String,
    @ColumnInfo(name = "server_message_id") val serverMessageId: Long?,
    @ColumnInfo(name = "sender_id") val senderId: Int,
    @ColumnInfo(name = "content_kind") val contentKind: String,
    val text: String?,
    @ColumnInfo(name = "file_id") val fileId: String?,
    @ColumnInfo(name = "thumbnail_file_id") val thumbnailFileId: String?,
    val transcript: String?,
    @ColumnInfo(name = "file_name") val fileName: String?,
    @ColumnInfo(name = "file_size") val fileSize: Long?,
    @ColumnInfo(name = "mime_type") val mimeType: String?,
    val width: Int?,
    val height: Int?,
    val duration: Int?,
    @ColumnInfo(name = "card_json") val cardJson: String?,
    @ColumnInfo(name = "fallback_text") val fallbackText: String?,
    @ColumnInfo(name = "reply_to_message_id") val replyToMessageId: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "send_state") val sendState: Int
)

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey @ColumnInfo(name = "user_id") val userId: Int,
    val username: String,
    val nickname: String,
    @ColumnInfo(name = "avatar_url") val avatarUrl: String,
    val alias: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(tableName = "my_profile")
data class MyProfileEntity(
    @PrimaryKey @ColumnInfo(name = "user_id") val userId: Int,
    val username: String,
    val nickname: String,
    @ColumnInfo(name = "avatar_url") val avatarUrl: String,
    val signature: String,
    val phone: String?,
    val email: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(
    tableName = "users_cache",
    indices = [Index(name = "idx_users_cache_username", value = ["username"])]
)
data class UserCacheEntity(
    @PrimaryKey @ColumnInfo(name = "user_id") val userId: Int,
    val username: String,
    val nickname: String,
    @ColumnInfo(name = "avatar_url") val avatarUrl: String,
    val signature: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(
    tableName = "pending_requests",
    indices = [Index(name = "idx_pending_requests_sort", value = ["created_at"])]
)
data class PendingRequestEntity(
    @PrimaryKey @ColumnInfo(name = "request_id") val requestId: Long,
    @ColumnInfo(name = "from_user_id") val fromUserId: Int,
    @ColumnInfo(name = "to_user_id") val toUserId: Int,
    val message: String?,
    val status: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

@Entity(
    tableName = "agents_cache",
    indices = [
        Index(name = "idx_agents_featured", value = ["is_featured", "nickname"]),
        Index(name = "idx_agents_mine", value = ["is_mine", "nickname"])
    ]
)
data class AgentCacheEntity(
    @PrimaryKey @ColumnInfo(name = "user_id") val userId: Int,
    val username: String,
    val nickname: String,
    @ColumnInfo(name = "avatar_url") val avatarUrl: String,
    val signature: String,
    @ColumnInfo(name = "is_system_agent") val isSystemAgent: Int,
    @ColumnInfo(name = "mini_app_enabled") val miniAppEnabled: Int,
    @ColumnInfo(name = "mini_app_url") val miniAppUrl: String,
    @ColumnInfo(name = "mini_app_permissions") val miniAppPermissions: Int,
    val commands: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    val status: Int,
    @ColumnInfo(name = "is_featured", defaultValue = "0") val isFeatured: Int,
    @ColumnInfo(name = "is_mine", defaultValue = "0") val isMine: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(tableName = "blocked_users")
data class BlockedUserEntity(
    @PrimaryKey @ColumnInfo(name = "user_id") val userId: Int
)

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey @ColumnInfo(name = "group_id") val groupId: Int,
    val name: String,
    @ColumnInfo(name = "avatar_url") val avatarUrl: String,
    val description: String,
    @ColumnInfo(name = "owner_id") val ownerId: Int,
    val status: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(
    tableName = "group_members",
    primaryKeys = ["group_id", "user_id"],
    indices = [Index(name = "idx_group_members_group", value = ["group_id", "role", "display_name"])]
)
data class GroupMemberEntity(
    @ColumnInfo(name = "group_id") val groupId: Int,
    @ColumnInfo(name = "user_id") val userId: Int,
    val role: Int,
    @ColumnInfo(name = "joined_at") val joinedAt: Long,
    @ColumnInfo(name = "display_name") val displayName: String
)

@Entity(tableName = "media_files")
data class MediaFileEntity(
    @PrimaryKey @ColumnInfo(name = "file_id") val fileId: String,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "content_type") val contentType: String,
    val size: Long,
    val width: Int,
    val height: Int,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "thumbnail_file_id") val thumbnailFileId: String,
    @ColumnInfo(name = "public_url") val publicUrl: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)
