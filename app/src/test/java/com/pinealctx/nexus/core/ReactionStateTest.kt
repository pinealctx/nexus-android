package com.pinealctx.nexus.core

import com.shared.v1.MessageReactionView
import com.shared.v1.MessageReactions
import com.shared.v1.MessageReactionsChangedEvent
import org.junit.Assert.*
import org.junit.Test

class ReactionStateTest {
    private fun event(revision: Long, actor: Int, emoji: String, selected: List<String>) = MessageReactionsChangedEvent.newBuilder()
        .setState(MessageReactions.newBuilder().setConversationId(1).setMessageId(2).setRevision(revision))
        .setActorId(actor).setEmoji(emoji).setPresent(true).addAllActorSelectedEmojis(selected).build()

    @Test fun `reverse own events retain complete latest selection`() {
        val state = ReactionState(MessageReactionView.getDefaultInstance())
            .apply(event(2, 7, "❤️", listOf("👍", "❤️")), 7)
            .apply(event(1, 7, "👍", listOf("👍")), 7)
        assertEquals(listOf("👍", "❤️"), state.view.selectedEmojisList)
        assertEquals(2, state.view.state.revision)
    }
    @Test fun `newer shared counts do not hide an older own selection`() {
        val state = ReactionState(MessageReactionView.getDefaultInstance())
            .apply(event(3, 8, "😂", listOf("😂")), 7)
            .apply(event(2, 7, "👍", listOf("👍")), 7)
        assertEquals(3, state.view.state.revision)
        assertEquals(2, state.ownRevision)
        assertEquals(listOf("👍"), state.view.selectedEmojisList)
    }
    @Test fun `recall clears and stale RPC cannot restore selection`() {
        val stale = MessageReactionView.newBuilder().setState(MessageReactions.newBuilder().setRevision(1)).addSelectedEmojis("👍").build()
        val state = ReactionState(stale, 1).apply(event(2, 0, "", emptyList()), 7).merge(stale)
        assertTrue(state.view.selectedEmojisList.isEmpty())
        assertEquals(2, state.view.state.revision)
    }
}
