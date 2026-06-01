package com.pinealctx.nexus.data.repository

import com.pinealctx.nexus.core.AppEvent
import com.pinealctx.nexus.core.AppEventBus
import com.pinealctx.nexus.core.ContactData
import com.pinealctx.nexus.core.PendingRequestData
import com.pinealctx.nexus.core.managers.ContactManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactRepository @Inject constructor(
    private val contactManager: ContactManager,
    private val appEventBus: AppEventBus
) {
    fun observeContacts(): Flow<List<ContactData>> {
        return appEventBus.contactsUpdated()
            .onStart { emit(AppEvent.ContactsUpdated) }
            .map { contactManager.getContacts() }
            .flowOn(Dispatchers.IO)
    }

    fun getContacts(): List<ContactData> = contactManager.getContacts()

    suspend fun fetchFromRemote() {
        contactManager.fetchContacts()
    }

    suspend fun sendFriendRequest(targetUserId: Int, message: String) {
        contactManager.sendFriendRequest(targetUserId, message)
    }

    suspend fun acceptFriendRequest(requestId: Long) {
        contactManager.acceptFriendRequest(requestId)
    }

    suspend fun rejectFriendRequest(requestId: Long) {
        contactManager.rejectFriendRequest(requestId)
    }

    suspend fun deleteContact(userId: Int) {
        contactManager.deleteContact(userId)
    }

    fun getPendingRequests(): List<PendingRequestData> = contactManager.getPendingRequests()

    fun searchUsers(query: String): List<ContactData> = contactManager.searchUsers(query)

    suspend fun blockUser(userId: Int) {
        contactManager.blockUser(userId)
    }

    suspend fun unblockUser(userId: Int) {
        contactManager.unblockUser(userId)
    }

    fun getBlockedUsers(): List<Int> = contactManager.getBlockedUsers()
}
