package com.pinealctx.nexus.core.managers

import com.pinealctx.nexus.client.ContactApi
import com.pinealctx.nexus.core.ContactData
import com.pinealctx.nexus.core.MessageSearchResultData
import com.pinealctx.nexus.local.LocalDataStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchManager @Inject constructor(
    private val contactApi: ContactApi,
    private val localDataStore: LocalDataStore
) {
    suspend fun searchUsers(query: String): List<ContactData> = contactApi.searchUsers(query)

    fun searchMessages(
        query: String,
        conversationId: String? = null,
        limit: Int = 30,
        offset: Int = 0
    ): List<MessageSearchResultData> =
        localDataStore.searchMessages(query, conversationId, limit, offset)

    fun rebuildSearchIndex(): Long = localDataStore.messageCount()
}
