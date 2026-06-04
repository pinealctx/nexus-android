package com.pinealctx.nexus.client

import com.api.v1.AcceptFriendRequestRequest
import com.api.v1.AddContactRequest
import com.api.v1.BlockUserRequest
import com.api.v1.DeleteContactRequest
import com.api.v1.ListBlockedRequest
import com.api.v1.ListContactsRequest
import com.api.v1.ListPendingRequestsRequest
import com.api.v1.RejectFriendRequestRequest
import com.api.v1.SearchUsersRequest
import com.api.v1.SendFriendRequestRequest
import com.api.v1.UnblockUserRequest
import com.api.v1.UpdateContactAliasRequest
import com.pinealctx.nexus.core.ContactData
import com.pinealctx.nexus.core.PendingRequestData
import com.shared.v1.ContactItem
import com.shared.v1.PendingRequestItem
import com.shared.v1.UserInfo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactApi @Inject constructor(
    private val apiClientFactory: ApiClientFactory,
    private val headers: RpcHeaders
) {
    suspend fun listContacts(limit: Int = 200): List<ContactData> {
        val contacts = mutableListOf<ContactData>()
        var afterId: Int? = null

        do {
            val request = ListContactsRequest.newBuilder()
                .setLimit(limit)
                .apply {
                    afterId?.let { setAfterId(it) }
                }
                .build()
            val response = apiClientFactory.createClients()
                .contacts
                .listContacts(request, headers.current())
                .requireMessage()

            val page = response.contactsList.map { it.toData() }
            contacts += page
            afterId = page.lastOrNull()?.userId
        } while (response.hasMore && afterId != null)

        return contacts
    }

    suspend fun addContact(targetUserId: Int) {
        apiClientFactory.createClients()
            .contacts
            .addContact(
                request = AddContactRequest.newBuilder()
                    .setTargetUserId(targetUserId)
                    .build(),
                headers = headers.current()
            )
            .requireMessage()
    }

    suspend fun deleteContact(userId: Int) {
        apiClientFactory.createClients()
            .contacts
            .deleteContact(
                request = DeleteContactRequest.newBuilder()
                    .setContactUserId(userId)
                    .build(),
                headers = headers.current()
            )
            .requireMessage()
    }

    suspend fun updateContactAlias(contactUserId: Int, alias: String?) {
        apiClientFactory.createClients()
            .contacts
            .updateContactAlias(
                request = UpdateContactAliasRequest.newBuilder()
                    .setContactUserId(contactUserId)
                    .apply {
                        if (alias.isNullOrBlank()) clearAlias() else setAlias(alias)
                    }
                    .build(),
                headers = headers.current()
            )
            .requireMessage()
    }

    suspend fun searchUsers(query: String): List<ContactData> {
        if (query.isBlank()) return emptyList()
        return apiClientFactory.createClients()
            .contacts
            .searchUsers(
                request = SearchUsersRequest.newBuilder()
                    .setQuery(query)
                    .build(),
                headers = headers.current()
            )
            .requireMessage()
            .itemsList
            .map { it.toContactData() }
    }

    suspend fun sendFriendRequest(targetUserId: Int, message: String) {
        apiClientFactory.createClients()
            .contacts
            .sendFriendRequest(
                request = SendFriendRequestRequest.newBuilder()
                    .setTargetUserId(targetUserId)
                    .setMessage(message)
                    .build(),
                headers = headers.current()
            )
            .requireMessage()
    }

    suspend fun acceptFriendRequest(requestId: Long) {
        apiClientFactory.createClients()
            .contacts
            .acceptFriendRequest(
                request = AcceptFriendRequestRequest.newBuilder()
                    .setRequestId(requestId)
                    .build(),
                headers = headers.current()
            )
            .requireMessage()
    }

    suspend fun rejectFriendRequest(requestId: Long) {
        apiClientFactory.createClients()
            .contacts
            .rejectFriendRequest(
                request = RejectFriendRequestRequest.newBuilder()
                    .setRequestId(requestId)
                    .build(),
                headers = headers.current()
            )
            .requireMessage()
    }

    suspend fun listPendingRequests(beforeTime: Long? = null, limit: Int = 20): List<PendingRequestData> {
        return apiClientFactory.createClients()
            .contacts
            .listPendingRequests(
                request = ListPendingRequestsRequest.newBuilder()
                    .setLimit(limit)
                    .apply {
                        beforeTime?.let { setBeforeTime(it) }
                    }
                    .build(),
                headers = headers.current()
            )
            .requireMessage()
            .itemsList
            .map { it.toData() }
    }

    suspend fun blockUser(userId: Int) {
        apiClientFactory.createClients()
            .contacts
            .blockUser(
                request = BlockUserRequest.newBuilder()
                    .setTargetUserId(userId)
                    .build(),
                headers = headers.current()
            )
            .requireMessage()
    }

    suspend fun unblockUser(userId: Int) {
        apiClientFactory.createClients()
            .contacts
            .unblockUser(
                request = UnblockUserRequest.newBuilder()
                    .setTargetUserId(userId)
                    .build(),
                headers = headers.current()
            )
            .requireMessage()
    }

    suspend fun listBlockedUsers(): List<Int> {
        return apiClientFactory.createClients()
            .contacts
            .listBlocked(ListBlockedRequest.getDefaultInstance(), headers.current())
            .requireMessage()
            .blockedList
            .map { it.user.userId }
    }
}

private fun ContactItem.toData(): ContactData =
    user.toContactData(alias = if (hasAlias()) alias else null)

private fun UserInfo.toContactData(alias: String? = null): ContactData =
    ContactData(
        userId = userId,
        username = username,
        nickname = nickname,
        avatarUrl = avatarUrl,
        alias = alias
    )

private fun PendingRequestItem.toData(): PendingRequestData =
    PendingRequestData(
        requestId = requestId,
        fromUserId = fromUserId,
        toUserId = toUserId,
        message = message.takeIf { it.isNotBlank() },
        status = 1,
        createdAt = createdAt
    )
