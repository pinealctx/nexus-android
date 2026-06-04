package com.pinealctx.nexus.core.managers

import com.pinealctx.nexus.client.ContactApi
import com.pinealctx.nexus.core.ContactData
import com.pinealctx.nexus.core.PendingRequestData
import com.pinealctx.nexus.local.LocalDataStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking

@Singleton
class ContactManager @Inject constructor(
    private val contactApi: ContactApi,
    private val localDataStore: LocalDataStore
) {
    fun getContacts(): List<ContactData> {
        val cached = localDataStore.listContacts()
        if (cached.isNotEmpty()) return cached

        return runBlocking {
            contactApi.listContacts()
                .also { localDataStore.upsertContacts(it) }
        }
    }

    fun fetchContacts() {
        runBlocking {
            contactApi.listContacts()
                .also { localDataStore.upsertContacts(it) }
        }
    }

    fun deleteContact(userId: Int) {
        runBlocking { contactApi.deleteContact(userId) }
        localDataStore.deleteContact(userId)
    }

    fun addContact(targetUserId: Int) {
        runBlocking { contactApi.addContact(targetUserId) }
        fetchContacts()
    }

    fun updateContactAlias(contactUserId: Int, alias: String?) {
        runBlocking { contactApi.updateContactAlias(contactUserId, alias) }
        localDataStore.updateContactAlias(contactUserId, alias)
    }

    fun searchUsers(query: String): List<ContactData> =
        runBlocking { contactApi.searchUsers(query) }

    fun sendFriendRequest(targetUserId: Int, message: String) {
        runBlocking { contactApi.sendFriendRequest(targetUserId, message) }
    }

    fun acceptFriendRequest(requestId: Long) {
        runBlocking { contactApi.acceptFriendRequest(requestId) }
        localDataStore.removePendingRequest(requestId)
        fetchContacts()
    }

    fun rejectFriendRequest(requestId: Long) {
        runBlocking { contactApi.rejectFriendRequest(requestId) }
        localDataStore.removePendingRequest(requestId)
    }

    fun getPendingRequests(): List<PendingRequestData> =
        listPendingRequests()

    fun listPendingRequests(beforeTime: Long? = null, limit: Int = 20): List<PendingRequestData> {
        val cached = localDataStore.listPendingRequests(beforeTime, limit)
        if (cached.isNotEmpty()) return cached

        return runBlocking {
            contactApi.listPendingRequests(beforeTime, limit)
                .also { localDataStore.upsertPendingRequests(it) }
        }
    }

    fun blockUser(userId: Int) {
        runBlocking { contactApi.blockUser(userId) }
        localDataStore.setBlockedUser(userId, true)
    }

    fun unblockUser(userId: Int) {
        runBlocking { contactApi.unblockUser(userId) }
        localDataStore.setBlockedUser(userId, false)
    }

    fun getBlockedUsers(): List<Int> {
        val cached = localDataStore.listBlockedUsers()
        if (cached.isNotEmpty()) return cached

        return runBlocking {
            contactApi.listBlockedUsers()
                .also { localDataStore.replaceBlockedUsers(it) }
        }
    }
}
