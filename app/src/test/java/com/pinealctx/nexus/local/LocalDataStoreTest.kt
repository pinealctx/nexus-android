package com.pinealctx.nexus.local

import android.content.Context
import com.pinealctx.nexus.core.AgentCommandData
import com.pinealctx.nexus.core.AgentInfoData
import com.pinealctx.nexus.core.ContactData
import com.pinealctx.nexus.core.GroupData
import com.pinealctx.nexus.core.GroupMemberData
import com.pinealctx.nexus.core.LocalMessageData
import com.pinealctx.nexus.core.MediaFileData
import com.pinealctx.nexus.core.MessageContent
import com.pinealctx.nexus.core.MessageData
import com.pinealctx.nexus.core.MessageReplyContextData
import com.pinealctx.nexus.core.MessageSendState
import com.pinealctx.nexus.core.PendingRequestData
import com.pinealctx.nexus.core.ProfileData
import com.shared.v1.ConversationActionType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class LocalDataStoreTest {
    private lateinit var context: Context
    private lateinit var store: LocalDataStore

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("nexus.db")
        store = LocalDataStore(context)
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase("nexus.db")
    }

    @Test
    fun `upserting messages creates conversation preview and supports pagination`() {
        store.upsertMessages(
            listOf(
                message(id = 1L, text = "first", createdAt = 100L),
                message(id = 2L, text = "second", createdAt = 200L),
                message(id = 3L, text = "third", createdAt = 300L)
            )
        )

        val conversations = store.listConversations()
        assertEquals(1, conversations.size)
        assertEquals("100", conversations.single().conversationId)
        assertEquals(3L, conversations.single().lastMessageId)
        assertEquals("third", conversations.single().lastMessageContent)

        assertEquals(listOf(3L, 2L), store.listMessages(100L, limit = 2).map { it.messageId })
        assertEquals(listOf(1L), store.listMessages(100L, beforeId = 2L).map { it.messageId })
    }

    @Test
    fun `deleting messages refreshes conversation preview`() {
        store.upsertMessages(
            listOf(
                message(id = 1L, text = "first", createdAt = 100L),
                message(id = 2L, text = "second", createdAt = 200L),
                message(id = 3L, text = "third", createdAt = 300L)
            )
        )

        store.deleteMessages(100L, listOf(3L))

        val conversation = store.getConversation(100L)
        assertEquals(2L, conversation?.lastMessageId)
        assertEquals("second", conversation?.lastMessageContent)

        store.deleteHistory(100L, upToMessageId = 2L)

        assertEquals(emptyList<MessageData>(), store.listMessages(100L))
        assertEquals(0L, store.getConversation(100L)?.lastMessageId)
        assertNull(store.getConversation(100L)?.lastMessageContent)
    }

    @Test
    fun `editing and recalling messages update local cache and preview`() {
        store.upsertMessage(message(id = 1L, text = "original"))

        store.editMessage(conversationId = 100L, messageId = 1L, text = "edited")

        val edited = store.listMessages(100L).single()
        assertEquals(MessageContent.Text("edited"), edited.content)
        assertTrue(edited.edited)
        assertEquals("edited", store.getConversation(100L)?.lastMessageContent)

        store.recallMessage(conversationId = 100L, messageId = 1L)

        val recalled = store.listMessages(100L).single()
        assertEquals(MessageContent.Recalled, recalled.content)
        assertTrue(recalled.recalled)
        assertEquals("[Message recalled]", store.getConversation(100L)?.lastMessageContent)
    }

    @Test
    fun `messages persist reply context`() {
        store.upsertMessage(
            message(
                id = 2L,
                text = "reply",
                replyToMessageId = 1L,
                replyContext = MessageReplyContextData(
                    messageId = 1L,
                    senderId = 7,
                    senderNickname = "Bob",
                    contentPreview = "original"
                )
            )
        )

        val message = store.listMessages(100L).single()
        assertEquals(1L, message.replyToMessageId)
        assertEquals(MessageReplyContextData(1L, 7, "Bob", "original"), message.replyContext)
    }

    @Test
    fun `local messages track send state server id and cleanup`() {
        store.upsertLocalMessage(
            localMessage(
                clientMessageId = 101L,
                text = "sending",
                createdAt = 100L,
                replyToMessageId = 90L,
                sendState = MessageSendState.SENDING
            )
        )
        store.upsertLocalMessage(
            localMessage(
                clientMessageId = 102L,
                text = "failed",
                createdAt = 200L,
                sendState = MessageSendState.SENDING
            )
        )

        store.markLocalMessageSent(clientMessageId = 101L, serverMessageId = 501L)
        store.markLocalMessageFailed(clientMessageId = 102L)

        val localMessages = store.listLocalMessages(100L)
        assertEquals(listOf(102L, 101L), localMessages.map { it.clientMessageId })
        assertEquals(MessageSendState.FAILED, localMessages[0].sendState)
        assertEquals(MessageSendState.SENT, localMessages[1].sendState)
        assertEquals(501L, localMessages[1].serverMessageId)
        assertEquals(90L, localMessages[1].replyToMessageId)

        assertEquals(MessageSendState.FAILED, store.getLocalMessage(102L)?.sendState)
        store.markLocalMessageSent(clientMessageId = 102L, serverMessageId = 502L)
        store.markLocalMessageSending(clientMessageId = 102L)
        val retryingMessage = store.getLocalMessage(102L)
        assertEquals(MessageSendState.SENDING, retryingMessage?.sendState)
        assertNull(retryingMessage?.serverMessageId)

        store.deleteMessages(100L, listOf(501L))
        assertEquals(listOf(102L), store.listLocalMessages(100L).map { it.clientMessageId })

        store.deleteLocalMessage(102L)
        assertEquals(emptyList<LocalMessageData>(), store.listLocalMessages(100L))
    }

    @Test
    fun `clearing conversation history removes local messages`() {
        store.upsertLocalMessage(localMessage(clientMessageId = 201L, text = "draft-send"))

        store.applyConversationAction(
            conversationId = 100L,
            action = ConversationActionType.CONVERSATION_ACTION_TYPE_DELETE,
            clearMessages = true
        )

        assertEquals(emptyList<LocalMessageData>(), store.listLocalMessages(100L))
    }

    @Test
    fun `local messages persist media content`() {
        store.upsertLocalMessage(
            localMessage(
                clientMessageId = 301L,
                content = MessageContent.Image(fileId = "img-1", width = 320, height = 240)
            )
        )
        store.upsertLocalMessage(
            localMessage(
                clientMessageId = 302L,
                content = MessageContent.File(
                    fileId = "file-1",
                    name = "report.pdf",
                    size = 4096L,
                    mimeType = "application/pdf"
                )
            )
        )

        val localMessages = store.listLocalMessages(100L)
        assertEquals(MessageContent.File("file-1", "report.pdf", 4096L, "application/pdf"), localMessages[0].content)
        assertEquals(MessageContent.Image("img-1", 320, 240), localMessages[1].content)
    }

    @Test
    fun `local messages persist rich content`() {
        store.upsertLocalMessage(
            localMessage(
                clientMessageId = 303L,
                content = MessageContent.Audio(fileId = "audio-1", duration = 1200)
            )
        )
        store.upsertLocalMessage(
            localMessage(
                clientMessageId = 304L,
                content = MessageContent.Video(fileId = "video-1", duration = 2400, width = 640, height = 360)
            )
        )
        store.upsertLocalMessage(
            localMessage(
                clientMessageId = 305L,
                content = MessageContent.Markdown("# hello")
            )
        )
        store.upsertLocalMessage(
            localMessage(
                clientMessageId = 306L,
                content = MessageContent.Card(json = """{"type":"AdaptiveCard"}""", fallbackText = "card")
            )
        )

        val localMessages = store.listLocalMessages(100L)
        assertEquals(MessageContent.Card("""{"type":"AdaptiveCard"}""", "card"), localMessages[0].content)
        assertEquals(MessageContent.Markdown("# hello"), localMessages[1].content)
        assertEquals(MessageContent.Video("video-1", 2400, 640, 360), localMessages[2].content)
        assertEquals(MessageContent.Audio("audio-1", 1200), localMessages[3].content)
    }

    @Test
    fun `upserting remote message removes matching sent local echo`() {
        store.upsertLocalMessage(localMessage(clientMessageId = 401L, text = "echo"))
        store.markLocalMessageSent(clientMessageId = 401L, serverMessageId = 601L)

        store.upsertMessage(message(id = 601L, text = "echo"))

        assertEquals(emptyList<LocalMessageData>(), store.listLocalMessages(100L))
        assertEquals(listOf(601L), store.listMessages(100L).map { it.messageId })
    }

    @Test
    fun `conversation actions update visibility mute state and local history`() {
        store.upsertMessage(message(id = 1L, text = "hello"))

        store.applyConversationAction(
            conversationId = 100L,
            action = ConversationActionType.CONVERSATION_ACTION_TYPE_MUTE,
            clearMessages = false
        )
        assertTrue(store.getConversation(100L)?.isMuted == true)

        store.applyConversationAction(
            conversationId = 100L,
            action = ConversationActionType.CONVERSATION_ACTION_TYPE_DELETE,
            clearMessages = true
        )

        assertNull(store.getConversation(100L))
        assertEquals(emptyList<MessageData>(), store.listMessages(100L))
    }

    @Test
    fun `contacts pending requests and blocked users round trip`() {
        store.upsertContacts(
            listOf(
                ContactData(2, "bob", "Bob", "b.png", null),
                ContactData(1, "alice", "Alice", "a.png", "Zed")
            )
        )
        store.updateContactAlias(2, "Amy")

        assertEquals(listOf(2, 1), store.listContacts().map { it.userId })
        assertEquals("Amy", store.listContacts().first().alias)

        store.upsertPendingRequest(
            PendingRequestData(
                requestId = 9L,
                fromUserId = 2,
                toUserId = 1,
                message = "hi",
                status = 1,
                createdAt = 999L
            )
        )
        assertEquals(9L, store.listPendingRequests().single().requestId)
        store.removePendingRequest(9L)
        assertEquals(emptyList<PendingRequestData>(), store.listPendingRequests())

        store.replaceBlockedUsers(listOf(7, 3))
        store.setBlockedUser(5, true)
        store.setBlockedUser(3, false)
        assertEquals(listOf(5, 7), store.listBlockedUsers())
    }

    @Test
    fun `profile and user cache round trip`() {
        store.upsertMyProfile(
            ProfileData(
                userId = 1,
                username = "alice",
                nickname = "Alice",
                avatarUrl = "alice.png",
                signature = "hello",
                phone = "+10000000000",
                email = "alice@example.com"
            )
        )
        store.upsertUsers(
            listOf(
                ContactData(2, "bob", "Bob", "bob.png", null),
                ContactData(3, "carol", "Carol", "carol.png", null)
            )
        )

        assertEquals("alice", store.getMyProfile()?.username)
        assertEquals("Alice", store.getUser(1)?.nickname)
        assertEquals(2, store.getUserByUsername("BOB")?.userId)
        assertEquals(listOf(3, 2), store.getUsers(listOf(3, 9, 2)).map { it.userId })

        store.deleteUserCache(2)
        assertNull(store.getUser(2))
    }

    @Test
    fun `agents preserve featured mine flags commands and status`() {
        val featuredAgent = agent(
            userId = 10,
            username = "helper",
            nickname = "Helper",
            command = "/help",
            status = 1
        )
        val myAgent = agent(
            userId = 11,
            username = "builder",
            nickname = "Builder",
            command = "/build",
            status = 1
        )

        store.upsertAgent(featuredAgent, featured = true)
        store.upsertAgent(myAgent, mine = true)
        store.updateAgentStatus(10, status = 3)

        assertEquals(listOf(10), store.listFeaturedAgents().map { it.userId })
        assertEquals(listOf(11), store.listMyAgents().map { it.userId })
        assertEquals("/help", store.getAgent(10)?.commands?.single()?.command)
        assertEquals(3, store.getAgent(10)?.status)

        store.deleteAgent(10)
        assertNull(store.getAgent(10))
    }

    @Test
    fun `groups and members round trip and delete together`() {
        store.upsertGroups(
            listOf(
                GroupData(
                    groupId = 20,
                    name = "Design",
                    avatarUrl = "design.png",
                    description = "old",
                    ownerId = 1,
                    status = 1
                )
            )
        )
        store.updateGroupName(20, "Product")
        store.updateGroupDescription(20, "new")
        store.replaceGroupMembers(
            20,
            listOf(
                GroupMemberData(userId = 2, role = 2, joinedAt = 200L, displayName = "Bob"),
                GroupMemberData(userId = 1, role = 1, joinedAt = 100L, displayName = "Alice")
            )
        )

        assertEquals("Product", store.getGroup(20)?.name)
        assertEquals("new", store.listGroups().single().description)
        assertEquals(listOf(1, 2), store.listGroupMembers(20).map { it.userId })

        store.removeGroupMember(20, 2)
        assertEquals(listOf(1), store.listGroupMembers(20).map { it.userId })

        store.deleteGroup(20)
        assertNull(store.getGroup(20))
        assertEquals(emptyList<GroupMemberData>(), store.listGroupMembers(20))
    }

    @Test
    fun `media files round trip and public url refresh`() {
        store.upsertMediaFile(
            MediaFileData(
                fileId = "file-1",
                fileName = "photo.jpg",
                contentType = "image/jpeg",
                size = 1024L,
                width = 640,
                height = 480,
                durationMs = 0L,
                thumbnailFileId = "thumb-1",
                publicUrl = "https://old.example.com/photo.jpg"
            )
        )

        assertEquals("photo.jpg", store.getMediaFile("file-1")?.fileName)

        store.updateMediaPublicUrl("file-1", "https://new.example.com/photo.jpg")
        store.updateMediaPublicUrl("file-2", "https://new.example.com/unknown.bin")

        assertEquals("https://new.example.com/photo.jpg", store.getMediaFile("file-1")?.publicUrl)
        assertEquals("https://new.example.com/unknown.bin", store.getMediaFile("file-2")?.publicUrl)
    }

    @Test
    fun `search messages uses cached renderable fields`() {
        store.upsertMessages(
            listOf(
                message(id = 1L, text = "hello world"),
                message(
                    id = 2L,
                    content = MessageContent.Card(json = """{"type":"AdaptiveCard"}""", fallbackText = "approval card")
                ),
                message(
                    id = 3L,
                    content = MessageContent.File(fileId = "f", name = "roadmap.pdf", size = 50L)
                )
            )
        )

        assertEquals(listOf(1L), store.searchMessages("world", null, 10, 0).map { it.messageId })
        assertEquals(listOf(2L), store.searchMessages("approval", "100", 10, 0).map { it.messageId })
        assertEquals(listOf(3L), store.searchMessages("roadmap", null, 10, 0).map { it.messageId })
    }

    private fun message(
        id: Long,
        text: String = "",
        createdAt: Long = id,
        content: MessageContent = MessageContent.Text(text),
        replyToMessageId: Long? = null,
        replyContext: MessageReplyContextData? = null
    ): MessageData =
        MessageData(
            conversationId = "100",
            messageId = id,
            senderId = 42,
            content = content,
            replyToMessageId = replyToMessageId,
            replyContext = replyContext,
            createdAt = createdAt,
            edited = false,
            recalled = content is MessageContent.Recalled
        )

    private fun localMessage(
        clientMessageId: Long,
        text: String = "local-$clientMessageId",
        createdAt: Long = clientMessageId,
        sendState: MessageSendState = MessageSendState.SENDING,
        content: MessageContent = MessageContent.Text(text),
        replyToMessageId: Long? = null
    ): LocalMessageData =
        LocalMessageData(
            clientMessageId = clientMessageId,
            conversationId = "100",
            serverMessageId = null,
            senderId = 1,
            content = content,
            replyToMessageId = replyToMessageId,
            createdAt = createdAt,
            sendState = sendState
        )

    private fun agent(
        userId: Int,
        username: String,
        nickname: String,
        command: String,
        status: Int
    ): AgentInfoData =
        AgentInfoData(
            userId = userId,
            username = username,
            nickname = nickname,
            avatarUrl = "$username.png",
            signature = "$nickname signature",
            isSystemAgent = false,
            miniAppEnabled = true,
            miniAppUrl = "https://example.com/$username",
            miniAppPermissions = 7,
            commands = listOf(AgentCommandData(command, "$command description")),
            createdAt = 123L,
            status = status
        )
}
