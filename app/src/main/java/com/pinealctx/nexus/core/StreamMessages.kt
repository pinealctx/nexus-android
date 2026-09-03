package com.pinealctx.nexus.core

internal fun mergeStreamContent(
    existing: MessageContent.Stream?,
    incoming: MessageContent.Stream
): MessageContent.Stream {
    if (existing?.phase?.isTerminal == true && !incoming.phase.isTerminal) {
        return existing
    }
    if (
        incoming.phase == MessageStreamPhase.DELTA &&
        incoming.sequence > 0 &&
        existing != null &&
        existing.sequence >= incoming.sequence
    ) {
        return existing
    }

    val previousText = existing?.accumulatedText.orEmpty()
    val accumulatedText = when (incoming.phase) {
        MessageStreamPhase.START,
        MessageStreamPhase.UNSPECIFIED -> incoming.accumulatedText.ifBlank { previousText }
        MessageStreamPhase.DELTA -> if (incoming.delta.isNotEmpty()) {
            incoming.accumulatedText.ifBlank { previousText } + incoming.delta
        } else {
            incoming.accumulatedText.ifBlank { previousText }
        }
        MessageStreamPhase.END,
        MessageStreamPhase.ERROR -> incoming.accumulatedText.ifBlank { previousText + incoming.delta }
    }

    return incoming.copy(
        sequence = maxOf(existing?.sequence ?: 0, incoming.sequence),
        delta = "",
        contentType = incoming.contentType.ifBlank { existing?.contentType.orEmpty() },
        accumulatedText = accumulatedText,
        errorMessage = incoming.errorMessage?.takeIf { it.isNotBlank() }
            ?: existing?.errorMessage?.takeIf { incoming.phase == MessageStreamPhase.ERROR }
    )
}
