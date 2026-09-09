package com.pinealctx.nexus.core

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DraftRepository @Inject constructor(private val store: com.pinealctx.nexus.local.LocalDataStore) {

    fun save(conversationId: String, text: String, entities: List<TextEntityData> = emptyList()) {
        store.saveDraft(conversationId, text, entities)
    }

    fun get(conversationId: String): String {
        return store.getDraft(conversationId).first
    }

    fun getEntities(conversationId: String): List<TextEntityData> = store.getDraft(conversationId).second

    fun clear(conversationId: String) {
        store.saveDraft(conversationId, "", emptyList())
    }

    fun clearAll() {
        store.clearDrafts()
    }
}
