package com.pinealctx.nexus.core

import com.shared.v1.MessageReactionView
import com.shared.v1.MessageReactionsChangedEvent

data class ReactionState(val view: MessageReactionView, val ownRevision: Long = 0) {
    fun merge(incoming: MessageReactionView): ReactionState {
        val revision = incoming.state.revision
        val builder = view.toBuilder()
        if (revision >= view.state.revision) builder.state = incoming.state
        if (revision >= ownRevision) builder.clearSelectedEmojis().addAllSelectedEmojis(incoming.selectedEmojisList)
        return ReactionState(builder.build(), maxOf(ownRevision, revision))
    }

    fun apply(event: MessageReactionsChangedEvent, userId: Int): ReactionState {
        val revision = event.state.revision
        val builder = view.toBuilder()
        if (revision > view.state.revision) builder.state = event.state
        var own = ownRevision
        if (revision > ownRevision && (event.actorId == userId || event.emoji.isEmpty())) {
            builder.clearSelectedEmojis().addAllSelectedEmojis(event.actorSelectedEmojisList)
            own = revision
        }
        return ReactionState(builder.build(), own)
    }
}
