package com.pinealctx.nexus.core

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DraftRepository @Inject constructor() {

    private val drafts = mutableMapOf<String, String>()
    private val draftEntities = mutableMapOf<String, List<TextEntityData>>()

    fun save(conversationId: String, text: String, entities: List<TextEntityData> = emptyList()) {
        if (text.isBlank()) draftEntities.remove(conversationId)
        else draftEntities[conversationId] = validTextEntities(text, entities)
        if (text.isBlank()) {
            drafts.remove(conversationId)
        } else {
            drafts[conversationId] = text
        }
    }

    fun get(conversationId: String): String {
        return drafts[conversationId] ?: ""
    }

    fun getEntities(conversationId: String): List<TextEntityData> = draftEntities[conversationId].orEmpty()

    fun clear(conversationId: String) {
        draftEntities.remove(conversationId)
        drafts.remove(conversationId)
    }

    fun clearAll() {
        draftEntities.clear()
        drafts.clear()
    }
}
