package com.pinealctx.nexus.core.managers

import com.pinealctx.nexus.client.ContactApi
import com.pinealctx.nexus.core.ContactData
import com.pinealctx.nexus.core.PendingRequestData
import com.pinealctx.nexus.local.LocalDataStore
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactManager @Inject constructor(
    private val contactApi: ContactApi,
    private val localDataStore: LocalDataStore
) {
    fun observeContacts(): Flow<List<ContactData>> = localDataStore.observeContacts()

    fun observePendingRequests(): Flow<List<PendingRequestData>> =
        localDataStore.observePendingRequests()

    suspend fun getContacts(): List<ContactData> {
        val cached = localDataStore.listContacts()
        if (cached.isNotEmpty()) return cached

        return contactApi.listContacts()
            .also { localDataStore.upsertContacts(it) }
    }

    suspend fun fetchContacts() {
        contactApi.listContacts().also { localDataStore.upsertContacts(it) }
    }

    suspend fun deleteContact(userId: Int) {
        contactApi.deleteContact(userId)
        localDataStore.deleteContact(userId)
    }

    suspend fun addContact(targetUserId: Int) {
        contactApi.addContact(targetUserId)
        fetchContacts()
    }

    suspend fun updateContactAlias(contactUserId: Int, alias: String?) {
        contactApi.updateContactAlias(contactUserId, alias)
        localDataStore.updateContactAlias(contactUserId, alias)
    }

    suspend fun searchUsers(query: String): List<ContactData> = contactApi.searchUsers(query)

    suspend fun sendFriendRequest(targetUserId: Int, message: String) {
        contactApi.sendFriendRequest(targetUserId, message)
    }

    suspend fun acceptFriendRequest(requestId: Long) {
        contactApi.acceptFriendRequest(requestId)
        localDataStore.removePendingRequest(requestId)
        fetchContacts()
    }

    suspend fun rejectFriendRequest(requestId: Long) {
        contactApi.rejectFriendRequest(requestId)
        localDataStore.removePendingRequest(requestId)
    }

    suspend fun getPendingRequests(): List<PendingRequestData> =
        listPendingRequests()

    suspend fun listPendingRequests(beforeTime: Long? = null, limit: Int = 20): List<PendingRequestData> {
        val cached = localDataStore.listPendingRequests(beforeTime, limit)
        if (cached.isNotEmpty()) return cached

        return contactApi.listPendingRequests(beforeTime, limit)
            .also { localDataStore.upsertPendingRequests(it) }
    }

    suspend fun blockUser(userId: Int) {
        contactApi.blockUser(userId)
        localDataStore.setBlockedUser(userId, true)
    }

    suspend fun unblockUser(userId: Int) {
        contactApi.unblockUser(userId)
        localDataStore.setBlockedUser(userId, false)
    }

    suspend fun getBlockedUsers(): List<Int> {
        val cached = localDataStore.listBlockedUsers()
        if (cached.isNotEmpty()) return cached

        return contactApi.listBlockedUsers()
            .also { localDataStore.replaceBlockedUsers(it) }
    }
}
