package com.pinealctx.nexus.core.managers

import com.pinealctx.nexus.core.ContactData
import com.pinealctx.nexus.core.NexusClientProvider
import com.pinealctx.nexus.core.PendingRequestData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactManager @Inject constructor(
    private val clientProvider: NexusClientProvider
) {
    fun getContacts(): List<ContactData> {
        val contacts = clientProvider.getOrNull()?.getContacts() ?: return emptyList()
        return contacts.map { it.toContactData() }
    }

    fun fetchContacts() { clientProvider.getOrNull()?.fetchContacts() }

    fun deleteContact(userId: Int) { clientProvider.getOrNull()?.deleteContact(userId) }

    fun addContact(targetUserId: Int) { clientProvider.getOrNull()?.addContact(targetUserId) }

    fun updateContactAlias(contactUserId: Int, alias: String?) {
        clientProvider.getOrNull()?.updateContactAlias(contactUserId, alias)
    }

    fun searchUsers(query: String): List<ContactData> {
        val results = clientProvider.getOrNull()?.searchUsers(query) ?: return emptyList()
        return results.map { it.toContactData() }
    }

    fun sendFriendRequest(targetUserId: Int, message: String) {
        clientProvider.getOrNull()?.sendFriendRequest(targetUserId, message)
    }

    fun acceptFriendRequest(requestId: Long) { clientProvider.getOrNull()?.acceptFriendRequest(requestId) }

    fun rejectFriendRequest(requestId: Long) { clientProvider.getOrNull()?.rejectFriendRequest(requestId) }

    fun getPendingRequests(): List<PendingRequestData> {
        val requests = clientProvider.getOrNull()?.getPendingRequests() ?: return emptyList()
        return requests.map { r ->
            PendingRequestData(r.requestId, r.fromUserId, r.toUserId, r.message, r.status, r.createdAt)
        }
    }

    fun listPendingRequests(beforeTime: Long? = null, limit: Int = 20): List<PendingRequestData> {
        val requests = clientProvider.getOrNull()?.listPendingRequests(beforeTime, limit) ?: return emptyList()
        return requests.map { r ->
            PendingRequestData(r.requestId, r.fromUserId, r.toUserId, r.message, r.status, r.createdAt)
        }
    }

    fun blockUser(userId: Int) { clientProvider.getOrNull()?.blockUser(userId) }

    fun unblockUser(userId: Int) { clientProvider.getOrNull()?.unblockUser(userId) }

    fun getBlockedUsers(): List<Int> = clientProvider.getOrNull()?.getBlockedUsers() ?: emptyList()
}

private fun uniffi.nexus_ffi.ContactInfo.toContactData() = ContactData(
    userId, username, nickname, avatarUrl, alias
)
