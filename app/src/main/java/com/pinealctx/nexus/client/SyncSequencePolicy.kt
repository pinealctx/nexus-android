package com.pinealctx.nexus.client

internal enum class SyncSequenceDecision {
    IGNORE,
    APPLY,
    FETCH_GAP
}

internal inline fun applySequencedUpdate(
    currentSn: Int,
    incomingSn: Int,
    allowGap: Boolean,
    apply: () -> Unit,
    commitSn: (Int) -> Unit
): SyncSequenceDecision {
    val decision = when {
        incomingSn <= currentSn -> SyncSequenceDecision.IGNORE
        allowGap && incomingSn > currentSn + 1 -> SyncSequenceDecision.FETCH_GAP
        else -> SyncSequenceDecision.APPLY
    }
    if (decision == SyncSequenceDecision.APPLY) {
        apply()
        commitSn(incomingSn)
    }
    return decision
}
