package com.pinealctx.nexus.core.managers

import com.api.v1.*
import com.pinealctx.nexus.client.*
import com.pinealctx.nexus.local.LocalDataStore
import com.shared.v1.ReactionConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReactionManager @Inject constructor(
    private val factory: ApiClientFactory,
    private val headers: RpcHeaders,
    private val auth: AuthApi,
    private val cache: LocalDataStore,
    private val secure: com.pinealctx.nexus.core.SecureStorage
) {
    suspend fun configuration(): ReactionConfig = auth.getClientConfig().reactions

    suspend fun set(conversationId: Long, messageId: Long, emoji: String, present: Boolean) {
        val owner = secure.getUserId()
        val session = secure.notificationSession()
        val clients = factory.createClients()
        val view = if (present) clients.messages.setMessageReaction(
            setMessageReactionRequest { this.conversationId = conversationId; this.messageId = messageId; this.emoji = emoji }, headers.current()
        ).requireMessage().reactions else clients.messages.removeMessageReaction(
            removeMessageReactionRequest { this.conversationId = conversationId; this.messageId = messageId; this.emoji = emoji }, headers.current()
        ).requireMessage().reactions
        if (owner == secure.getUserId() && session == secure.notificationSession() && secure.hasTokens()) cache.upsertReactions(view)
    }

    suspend fun reactors(conversationId: Long, messageId: Long, emoji: String, after: Int = 0): ListMessageReactorsResponse =
        factory.createClients().messages.listMessageReactors(listMessageReactorsRequest {
            this.conversationId = conversationId; this.messageId = messageId; this.emoji = emoji; afterUserId = after; limit = 30
        }, headers.current()).requireMessage()
}
