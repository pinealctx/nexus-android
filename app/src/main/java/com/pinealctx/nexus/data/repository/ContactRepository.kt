package com.pinealctx.nexus.data.repository

import com.pinealctx.nexus.core.ContactData
import com.pinealctx.nexus.core.PendingRequestData
import com.pinealctx.nexus.core.managers.ContactManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactRepository @Inject constructor(
    private val contactManager: ContactManager
) {
    fun observeContacts(): Flow<List<ContactData>> = contactManager.observeContacts()

    fun observePendingRequests(): Flow<List<PendingRequestData>> =
        contactManager.observePendingRequests()

    suspend fun getContacts(): List<ContactData> = contactManager.getContacts()

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

    suspend fun getPendingRequests(): List<PendingRequestData> = contactManager.getPendingRequests()

    suspend fun searchUsers(query: String): List<ContactData> = contactManager.searchUsers(query)

    suspend fun blockUser(userId: Int) {
        contactManager.blockUser(userId)
    }

    suspend fun unblockUser(userId: Int) {
        contactManager.unblockUser(userId)
    }

    suspend fun getBlockedUsers(): List<Int> = contactManager.getBlockedUsers()
}
