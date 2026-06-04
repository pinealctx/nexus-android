package com.pinealctx.nexus.client

import android.util.Log
import com.api.v1.GetCurrentStateRequest
import com.api.v1.Update
import com.api.v1.getDifferenceRequest
import com.pinealctx.nexus.core.AppEventBus
import com.pinealctx.nexus.core.PendingRequestData
import com.pinealctx.nexus.local.LocalDataStore
import com.shared.v1.ConversationActionType
import com.shared.v1.NonSnUpdate
import com.shared.v1.SnUpdate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncEngine @Inject constructor(
    private val apiClientFactory: ApiClientFactory,
    private val headers: RpcHeaders,
    private val stateStore: SyncStateStore,
    private val updateDecoder: GatewayUpdateDecoder,
    private val localDataStore: LocalDataStore,
    private val appEventBus: AppEventBus
) {
    private val syncMutex = Mutex()

    fun getLocalSn(): Int = stateStore.getLocalSn()

    suspend fun coldStart(): Int {
        val response = apiClientFactory.createClients()
            .sync
            .getCurrentState(
                request = GetCurrentStateRequest.getDefaultInstance(),
                headers = headers.current()
            )
            .requireMessage()

        val latestSn = response.state.latestSn
        stateStore.setLocalSn(latestSn)
        appEventBus.emitConversationsUpdated()
        appEventBus.emitContactsUpdated()
        Log.i("NexusSync", "Cold-start baseline set to sn=$latestSn")
        return latestSn
    }

    suspend fun fetchDifference(): Boolean = syncMutex.withLock {
        var currentSn = stateStore.getLocalSn()
        var coldStartRequired = false

        while (true) {
            val response = apiClientFactory.createClients()
                .sync
                .getDifference(
                    request = getDifferenceRequest {
                        sn = currentSn
                    },
                    headers = headers.current()
                )
                .requireMessage()

            if (response.updateTooLong) {
                appEventBus.emitColdStartRequired()
                coldStartRequired = true
                break
            }

            response.updatesList.forEach { update ->
                applySnUpdate(update, isPush = false, allowGapFetch = false)
            }

            currentSn = response.sn
            stateStore.setLocalSn(currentSn)
            if (!response.hasMore) break
        }

        Log.i("NexusSync", "Difference sync complete, local_sn=${stateStore.getLocalSn()}")
        coldStartRequired
    }

    suspend fun processGatewayUpdate(update: Update, isPush: Boolean) {
        when (val classified = updateDecoder.classify(update)) {
            is GatewayUpdate.Sequenced -> processSnUpdate(classified.payload, isPush)
            is GatewayUpdate.Ephemeral -> applyNonSnUpdate(classified.payload)
            is GatewayUpdate.Unknown -> Log.w("NexusSync", "Received gateway update without payload")
        }
    }

    fun clearLocalData() {
        stateStore.clearCurrentUser()
        localDataStore.clearAll()
    }

    private suspend fun processSnUpdate(update: SnUpdate, isPush: Boolean) {
        val currentSn = stateStore.getLocalSn()
        if (update.sn <= currentSn) return

        if (update.sn > currentSn + 1) {
            Log.w("NexusSync", "Gap detected: local_sn=$currentSn received=${update.sn}")
            fetchDifference()
            return
        }

        applySnUpdate(update, isPush = isPush, allowGapFetch = true)
    }

    private suspend fun applySnUpdate(update: SnUpdate, isPush: Boolean, allowGapFetch: Boolean) {
        val currentSn = stateStore.getLocalSn()
        if (update.sn <= currentSn) return
        if (allowGapFetch && update.sn > currentSn + 1) {
            fetchDifference()
            return
        }

        dispatchSnUpdate(update)
        stateStore.setLocalSn(update.sn)
        Log.i("NexusSync", "Applied sn=${update.sn} kind=${update.updateCase} push=$isPush")
    }

    private fun applyNonSnUpdate(update: NonSnUpdate) {
        when (update.updateCase) {
            NonSnUpdate.UpdateCase.MESSAGE_ENVELOPE ->
                appEventBus.emitMessagesUpdated(update.messageEnvelope.conversationId.toString())
            NonSnUpdate.UpdateCase.CARD_ACTION_ANSWER,
            NonSnUpdate.UpdateCase.CARD_ACTION ->
                appEventBus.emitConversationsUpdated()
            NonSnUpdate.UpdateCase.UPDATE_NOT_SET -> Unit
        }
    }

    private fun dispatchSnUpdate(update: SnUpdate) {
        when (update.updateCase) {
            SnUpdate.UpdateCase.MESSAGE_ENVELOPE -> {
                localDataStore.upsertMessage(update.messageEnvelope.toMessageData())
                appEventBus.emitMessagesUpdated(update.messageEnvelope.conversationId.toString())
                appEventBus.emitConversationsUpdated()
            }
            SnUpdate.UpdateCase.CONVERSATION_ACTION -> {
                localDataStore.applyConversationAction(
                    conversationId = update.conversationAction.conversationId,
                    action = update.conversationAction.action,
                    clearMessages = update.conversationAction.clearMessages
                )
                appEventBus.emitConversationsUpdated()
            }
            SnUpdate.UpdateCase.READ_RECEIPT -> {
                localDataStore.markConversationRead(
                    conversationId = update.readReceipt.conversationId,
                    lastReadMessageId = update.readReceipt.lastReadMessageId
                )
                appEventBus.emitConversationsUpdated()
            }
            SnUpdate.UpdateCase.MESSAGE_DELETED -> {
                if (update.messageDeleted.messageIdsList.isNotEmpty()) {
                    localDataStore.deleteMessages(
                        conversationId = update.messageDeleted.conversationId,
                        messageIds = update.messageDeleted.messageIdsList
                    )
                } else {
                    localDataStore.deleteHistory(
                        conversationId = update.messageDeleted.conversationId,
                        upToMessageId = update.messageDeleted.upToMessageId
                    )
                }
                appEventBus.emitMessagesUpdated(update.messageDeleted.conversationId.toString())
                appEventBus.emitConversationsUpdated()
            }
            SnUpdate.UpdateCase.FRIEND_REQUEST_RECEIVED -> {
                val request = update.friendRequestReceived.request
                localDataStore.upsertPendingRequest(
                    PendingRequestData(
                        requestId = request.requestId,
                        fromUserId = request.fromUserId,
                        toUserId = request.toUserId,
                        message = request.message.takeIf { it.isNotBlank() },
                        status = 1,
                        createdAt = request.createdAt
                    )
                )
                appEventBus.emitContactsUpdated()
            }
            SnUpdate.UpdateCase.FRIEND_REQUEST_ACCEPTED -> {
                localDataStore.removePendingRequest(update.friendRequestAccepted.request.requestId)
                localDataStore.clearContacts()
                appEventBus.emitContactsUpdated()
            }
            SnUpdate.UpdateCase.FRIEND_REQUEST_REJECTED -> {
                localDataStore.removePendingRequest(update.friendRequestRejected.requestId)
                appEventBus.emitContactsUpdated()
            }
            SnUpdate.UpdateCase.CONTACT_DELETED -> {
                localDataStore.deleteContact(update.contactDeleted.peerUserId)
                appEventBus.emitContactsUpdated()
            }
            SnUpdate.UpdateCase.USER_BLOCK_TOGGLED -> {
                localDataStore.setBlockedUser(
                    userId = update.userBlockToggled.targetUserId,
                    isBlocked = update.userBlockToggled.isBlocked
                )
                appEventBus.emitContactsUpdated()
            }
            SnUpdate.UpdateCase.CONTACT_ALIAS_UPDATED -> {
                localDataStore.updateContactAlias(
                    userId = update.contactAliasUpdated.contactUserId,
                    alias = update.contactAliasUpdated.newAlias.takeIf { it.isNotBlank() }
                )
                appEventBus.emitContactsUpdated()
            }
            SnUpdate.UpdateCase.USER_PROFILE_UPDATED -> {
                localDataStore.deleteUserCache(update.userProfileUpdated.userId)
                localDataStore.clearContacts()
                appEventBus.emitContactsUpdated()
            }
            SnUpdate.UpdateCase.FRIEND_REQUEST_SENT -> {
                localDataStore.clearContacts()
                appEventBus.emitContactsUpdated()
            }
            SnUpdate.UpdateCase.USERNAME_CHANGED -> {
                localDataStore.deleteUserCache(update.usernameChanged.userId)
                localDataStore.clearContacts()
                appEventBus.emitContactsUpdated()
            }
            SnUpdate.UpdateCase.CONTACT_ADDED -> {
                localDataStore.clearContacts()
                appEventBus.emitContactsUpdated()
            }
            SnUpdate.UpdateCase.AGENT_STATUS_CHANGED -> {
                localDataStore.updateAgentStatus(
                    agentUserId = update.agentStatusChanged.agentUserId,
                    status = update.agentStatusChanged.newStatus.number
                )
                localDataStore.clearContacts()
                appEventBus.emitAgentsUpdated()
                appEventBus.emitContactsUpdated()
            }
            SnUpdate.UpdateCase.REMOVED_FROM_GROUP -> {
                localDataStore.deleteGroup(update.removedFromGroup.groupId)
                localDataStore.applyConversationAction(
                    conversationId = update.removedFromGroup.groupId.toLong(),
                    action = ConversationActionType.CONVERSATION_ACTION_TYPE_DELETE,
                    clearMessages = true
                )
                appEventBus.emitConversationsUpdated()
            }
            SnUpdate.UpdateCase.GROUP_DISSOLVED -> {
                localDataStore.deleteGroup(update.groupDissolved.groupId)
                localDataStore.applyConversationAction(
                    conversationId = update.groupDissolved.groupId.toLong(),
                    action = ConversationActionType.CONVERSATION_ACTION_TYPE_DELETE,
                    clearMessages = true
                )
                appEventBus.emitConversationsUpdated()
            }
            SnUpdate.UpdateCase.UPDATE_NOT_SET -> Unit
        }
    }
}
