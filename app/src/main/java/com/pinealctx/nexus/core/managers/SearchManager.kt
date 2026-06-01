package com.pinealctx.nexus.core.managers

import com.pinealctx.nexus.core.ContactData
import com.pinealctx.nexus.core.MessageSearchResultData
import com.pinealctx.nexus.core.NexusClientProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchManager @Inject constructor(
    private val clientProvider: NexusClientProvider
) {
    fun searchUsers(query: String): List<ContactData> {
        val results = clientProvider.getOrNull()?.searchUsers(query) ?: return emptyList()
        return results.map { it.toContactData() }
    }

    fun searchMessages(
        query: String,
        conversationId: String? = null,
        limit: Int = 30,
        offset: Int = 0
    ): List<MessageSearchResultData> {
        val results = clientProvider.getOrNull()?.searchMessages(query, conversationId, limit, offset)
            ?: return emptyList()
        return results.map { r ->
            MessageSearchResultData(r.conversationId, r.messageId, r.senderId, r.textSnippet, r.createdAt)
        }
    }

    fun rebuildSearchIndex(): Long {
        return clientProvider.getOrNull()?.rebuildSearchIndex()?.toLong() ?: 0L
    }
}

private fun uniffi.nexus_ffi.ContactInfo.toContactData() = ContactData(
    userId, username, nickname, avatarUrl, alias
)
