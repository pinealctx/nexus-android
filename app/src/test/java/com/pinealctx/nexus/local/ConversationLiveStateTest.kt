package com.pinealctx.nexus.local

import android.app.Application
import com.pinealctx.nexus.core.*
import com.shared.v1.ConversationType
import com.shared.v1.ConversationActionType
import com.shared.v1.MessageEntityType
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ConversationLiveStateTest {
    private lateinit var store: LocalDataStore
    @Before fun setup() {
        RuntimeEnvironment.getApplication().deleteDatabase("nexus.db")
        store = LocalDataStore(RuntimeEnvironment.getApplication())
    }
    @After fun teardown() { store.close(); RuntimeEnvironment.getApplication().deleteDatabase("nexus.db") }

    @Test fun `live outbox reorders previews failure retry acknowledgement and removal`(): Unit = runBlocking {
        store.upsertConversations(listOf(conversation("100", 1, 10), conversation("200", 2, 20)))
        val events = Channel<List<ConversationData>>(Channel.UNLIMITED)
        val observer = launch { store.observeConversations().collect { events.send(it) } }
        suspend fun awaitState(predicate: (List<ConversationData>) -> Boolean): List<ConversationData> = withTimeout(10_000) {
            var result = events.receive()
            while (!predicate(result)) result = events.receive()
            result
        }
        try {
            assertEquals("200", awaitState { it.size == 2 }.first().conversationId)
            store.upsertLocalMessage(local(10, "100", 30))
            val pending = awaitState { it.first().localPreview != null }.first()
            assertEquals("100", pending.conversationId)
            assertEquals(1L, pending.lastMessageId)
            assertEquals(0L, pending.unreadCount)
            store.markLocalMessageFailed(10)
            awaitState { it.first().localPreview?.sendState == MessageSendState.FAILED }
            store.markLocalMessageSending(10)
            awaitState { it.first().localPreview?.sendState == MessageSendState.SENDING }
            store.markLocalMessageSent(10, 3)
            awaitState { it.first().localPreview?.sendState == MessageSendState.SENT }
            store.upsertMessage(message(3, "100", 30))
            awaitState { it.first().lastMessageId == 3L && it.first().localPreview == null }
            assertTrue(store.listLocalMessages(100).isEmpty())
            store.upsertLocalMessage(local(11, "200", 40))
            awaitState { it.first().conversationId == "200" }
            store.deleteLocalMessage(11)
            awaitState { it.first().conversationId == "100" }
        } finally { observer.cancelAndJoin() }
    }

    @Test fun `late list response cannot revert realtime preview read mute or deletion`() {
        store.upsertConversation(conversation("100", 1, 10))
        val versions = store.conversationVersions()
        store.upsertMessage(message(2, "100", 20))
        store.markConversationRead(100, 2)
        store.applyConversationAction(100, ConversationActionType.CONVERSATION_ACTION_TYPE_MUTE, false)
        store.upsertConversations(listOf(conversation("100", 1, 10)), versions)
        val current = store.getConversation(100)!!
        assertEquals(2L, current.lastMessageId)
        assertEquals(20L, current.lastMessageTime)
        assertEquals("message-2", current.lastMessageContent)
        assertEquals(2L, current.lastReadMessageId)
        assertTrue(current.isMuted)
        store.markConversationRead(100, 1)
        assertEquals(2L, store.getConversation(100)!!.lastReadMessageId)
        val deleteVersions = store.conversationVersions()
        store.applyConversationAction(100, ConversationActionType.CONVERSATION_ACTION_TYPE_DELETE, false)
        store.upsertConversations(listOf(current), deleteVersions)
        assertNull(store.getConversation(100))
    }

    @Test fun `stale same message summary does not undo a local edit`() {
        store.upsertMessage(message(1, "100", 10))
        val stale = store.getConversation(100)!!
        val versions = store.conversationVersions()
        store.editMessage(100, 1, "edited")
        store.upsertConversations(listOf(stale), versions)
        assertEquals("edited", store.getConversation(100)!!.lastMessageContent)
    }

    @Test fun `draft and entities survive restart and sync reset but not account clear`() = runBlocking {
        val entities = listOf(TextEntityData(MessageEntityType.MESSAGE_ENTITY_TYPE_MENTION, 0, 5, userId = 7))
        DraftRepository(store).save("100", "@Alex", entities)
        assertEquals("@Alex", store.observeConversations().first().single().draft)
        store.close()
        store = LocalDataStore(RuntimeEnvironment.getApplication())
        assertEquals(entities, DraftRepository(store).getEntities("100"))
        store.clearSyncedData()
        assertEquals("@Alex", DraftRepository(store).get("100"))
        DraftRepository(store).save("100", "")
        assertTrue(DraftRepository(store).getEntities("100").isEmpty())
        DraftRepository(store).save("100", "private draft")
        store.clearAll()
        assertEquals("", DraftRepository(store).get("100"))
    }

    @Test fun `expanded history survives live arrivals edits and deletions`() = runBlocking {
        store.upsertMessages((1L..120L).map { message(it, "100", it) })
        val events = Channel<List<MessageData>>(Channel.UNLIMITED)
        val observer = launch { store.observeMessageWindow("100", 21).collect { events.send(it) } }
        suspend fun next(predicate: (List<MessageData>) -> Boolean): List<MessageData> = withTimeout(10_000) {
            var list = events.receive()
            while (!predicate(list)) list = events.receive()
            list
        }
        try {
            assertEquals(100, next { it.isNotEmpty() }.size)
            store.upsertMessage(message(121, "100", 121))
            assertEquals(21L, next { it.first().messageId == 121L }.last().messageId)
            store.editMessage(100, 21, "edited")
            next { (it.last().content as MessageContent.Text).text == "edited" }
            store.deleteMessages(100, listOf(21))
            assertEquals(22L, next { it.last().messageId != 21L }.last().messageId)
        } finally { observer.cancelAndJoin() }
    }

    @Test fun `pending conversation outside first fifty enters first page`() = runBlocking {
        store.upsertConversations((1..75).map { conversation(it.toString(), it.toLong(), it.toLong()) })
        store.upsertLocalMessage(local(1, "1", 100))
        assertEquals("1", store.observeConversations(50).first().first().conversationId)
        assertEquals(75, store.observeConversations(100).first().size)
    }

    @Test fun `old failed local message does not hide a newer incoming message`() = runBlocking {
        store.upsertConversation(conversation("100", 2, 100))
        store.upsertLocalMessage(local(1, "100", 10).copy(sendState = MessageSendState.FAILED))
        assertNull(store.observeConversations().first().single().localPreview)
    }

    private fun conversation(id: String, messageId: Long, time: Long) = ConversationData(
        id, ConversationType.CONVERSATION_TYPE_PRIVATE, 7, "Chat $id", "", messageId, time, "message-$messageId", false, messageId
    )
    private fun message(id: Long, conversationId: String, time: Long) = MessageData(
        conversationId, id, 7, MessageContent.Text("message-$id"), null, null, time, false, false
    )
    private fun local(id: Long, conversationId: String, time: Long) = LocalMessageData(
        clientMessageId = id, conversationId = conversationId, serverMessageId = null, senderId = 7,
        content = MessageContent.Text("pending-$id"), replyToMessageId = null, createdAt = time, sendState = MessageSendState.SENDING
    )
}
