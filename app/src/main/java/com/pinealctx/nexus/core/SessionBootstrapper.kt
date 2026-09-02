package com.pinealctx.nexus.core

import android.util.Log
import com.pinealctx.nexus.core.managers.ContactManager
import com.pinealctx.nexus.core.managers.ConversationManager
import com.pinealctx.nexus.core.managers.GroupManager
import com.pinealctx.nexus.core.managers.MessageManager
import com.pinealctx.nexus.core.managers.UserManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionBootstrapper @Inject constructor(
    private val conversationManager: ConversationManager,
    private val contactManager: ContactManager,
    private val groupManager: GroupManager,
    private val messageManager: MessageManager,
    private val userManager: UserManager
) {
    suspend fun hydrate() = supervisorScope {
        val conversations = async {
            runCatching {
                runBackgroundRpcWithRetry("ConversationService.ListConversations") {
                    conversationManager.fetchAllConversations(pageSize = CONVERSATION_PAGE_SIZE)
                }
            }
                .onFailure { Log.w(TAG, "Failed to hydrate conversations", it) }
                .getOrDefault(emptyList())
        }
        val directoryJobs = listOf(
            async { hydratePart("contacts") { contactManager.fetchContacts() } },
            async { hydratePart("groups") { groupManager.fetchGroups() } },
            async { hydratePart("profile") { userManager.fetchProfile() } },
            async { hydratePart("pending requests") { contactManager.listPendingRequests(limit = 100) } },
            async { hydratePart("blocked users") { contactManager.getBlockedUsers() } }
        )

        val hydratedConversations = conversations.await()
        val prefetchSemaphore = Semaphore(MESSAGE_PREFETCH_CONCURRENCY)
        hydratedConversations
            .map { conversation ->
                async {
                    prefetchSemaphore.withPermit {
                        runCatching {
                            runBackgroundRpcWithRetry(
                                "MessageService.GetMessageHistory(${conversation.conversationId})"
                            ) {
                                messageManager.fetchMessagePage(
                                    conversationId = conversation.conversationId,
                                    limit = MESSAGE_PREFETCH_LIMIT
                                )
                            }
                        }.onFailure {
                            Log.w(TAG, "Failed to prefetch conversation ${conversation.conversationId}", it)
                        }
                    }
                }
            }
            .awaitAll()
        directoryJobs.awaitAll()

        Log.i(TAG, "Session hydration complete: conversations=${hydratedConversations.size}")
    }

    private suspend fun hydratePart(name: String, block: suspend () -> Unit) {
        runCatching {
            runBackgroundRpcWithRetry(name, call = block)
        }
            .onFailure { Log.w(TAG, "Failed to hydrate $name", it) }
    }

    private companion object {
        const val TAG = "NexusBootstrap"
        const val CONVERSATION_PAGE_SIZE = 100
        const val MESSAGE_PREFETCH_CONCURRENCY = 4
        const val MESSAGE_PREFETCH_LIMIT = 20
    }
}
