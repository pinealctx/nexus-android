package com.pinealctx.nexus.core

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Facade that coordinates session management, sync, and drafts.
 * Delegates to SessionManager, SyncCoordinator, and DraftRepository.
 */
@Singleton
class SyncManager @Inject constructor(
    private val sessionManager: SessionManager,
    private val syncCoordinator: SyncCoordinator,
    private val draftRepository: DraftRepository
) {
    var onForceLogout: (() -> Unit)?
        get() = syncCoordinator.onForceLogout
        set(value) { syncCoordinator.onForceLogout = value }

    var activeConversationId: String?
        get() = syncCoordinator.activeConversationId
        set(value) { syncCoordinator.activeConversationId = value }

    fun initialize() = syncCoordinator.initialize()

    suspend fun tryRestoreSession(): Boolean = sessionManager.tryRestoreSession()

    fun startSession() = syncCoordinator.startSession()

    fun saveTokens(accessToken: String, refreshToken: String, expiresIn: Int, userId: Int) =
        sessionManager.saveTokens(accessToken, refreshToken, expiresIn, userId)

    fun stopSession() = syncCoordinator.stopSession()

    fun saveDraft(conversationId: String, text: String, entities: List<TextEntityData> = emptyList()) = draftRepository.save(conversationId, text, entities)

    fun getDraftEntities(conversationId: String): List<TextEntityData> = draftRepository.getEntities(conversationId)

    fun getDraft(conversationId: String): String = draftRepository.get(conversationId)

    fun clearDraft(conversationId: String) = draftRepository.clear(conversationId)
}
