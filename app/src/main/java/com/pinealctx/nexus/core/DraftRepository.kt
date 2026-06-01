package com.pinealctx.nexus.core

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DraftRepository @Inject constructor() {

    private val drafts = mutableMapOf<String, String>()

    fun save(conversationId: String, text: String) {
        if (text.isBlank()) {
            drafts.remove(conversationId)
        } else {
            drafts[conversationId] = text
        }
    }

    fun get(conversationId: String): String {
        return drafts[conversationId] ?: ""
    }

    fun clear(conversationId: String) {
        drafts.remove(conversationId)
    }

    fun clearAll() {
        drafts.clear()
    }
}
