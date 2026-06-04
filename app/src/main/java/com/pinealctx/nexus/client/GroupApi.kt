package com.pinealctx.nexus.client

import com.api.v1.CreateGroupRequest
import com.api.v1.DissolveGroupRequest
import com.api.v1.GetGroupInfoRequest
import com.api.v1.InviteMembersRequest
import com.api.v1.LeaveGroupRequest
import com.api.v1.ListGroupsRequest
import com.api.v1.RemoveMemberRequest
import com.api.v1.UpdateGroupAvatarRequest
import com.api.v1.UpdateGroupDescriptionRequest
import com.api.v1.UpdateGroupNameRequest
import com.pinealctx.nexus.core.GroupData
import com.pinealctx.nexus.core.GroupMemberData
import com.shared.v1.GroupInfo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupApi @Inject constructor(
    private val apiClientFactory: ApiClientFactory,
    private val headers: RpcHeaders
) {
    suspend fun listGroups(limit: Int = 200): List<GroupData> {
        val groups = mutableListOf<GroupData>()
        var afterId: Int? = null

        do {
            val request = ListGroupsRequest.newBuilder()
                .setLimit(limit)
                .apply {
                    afterId?.let { setAfterId(it) }
                }
                .build()
            val response = apiClientFactory.createClients()
                .groups
                .listGroups(request, headers.current())
                .requireMessage()

            val page = response.groupsList.map { it.toData() }
            groups += page
            afterId = page.lastOrNull()?.groupId
        } while (response.hasMore && afterId != null)

        return groups
    }

    suspend fun getGroupInfo(groupId: Int): GroupData {
        return getGroupDetail(groupId).group.toData()
    }

    suspend fun getGroupMembers(groupId: Int): List<GroupMemberData> {
        val response = getGroupDetail(groupId)
        val users = response.usersList.associateBy { it.userId }

        return response.membersList.map { member ->
            val user = users[member.userId]
            GroupMemberData(
                userId = member.userId,
                role = member.role.number,
                joinedAt = member.joinedAt,
                displayName = user?.nickname?.takeIf { it.isNotBlank() } ?: user?.username ?: "user_${member.userId}"
            )
        }
    }

    suspend fun createGroup(name: String, memberIds: List<Int>): Int {
        val response = apiClientFactory.createClients()
            .groups
            .createGroup(
                request = CreateGroupRequest.newBuilder()
                    .setName(name)
                    .addAllMemberIds(memberIds)
                    .build(),
                headers = headers.current()
            )
            .requireMessage()

        return response.group.groupId
    }

    suspend fun dissolveGroup(groupId: Int) {
        apiClientFactory.createClients()
            .groups
            .dissolveGroup(
                request = DissolveGroupRequest.newBuilder()
                    .setGroupId(groupId)
                    .build(),
                headers = headers.current()
            )
            .requireMessage()
    }

    suspend fun leaveGroup(groupId: Int) {
        apiClientFactory.createClients()
            .groups
            .leaveGroup(
                request = LeaveGroupRequest.newBuilder()
                    .setGroupId(groupId)
                    .build(),
                headers = headers.current()
            )
            .requireMessage()
    }

    suspend fun updateGroupName(groupId: Int, name: String) {
        apiClientFactory.createClients()
            .groups
            .updateGroupName(
                request = UpdateGroupNameRequest.newBuilder()
                    .setGroupId(groupId)
                    .setName(name)
                    .build(),
                headers = headers.current()
            )
            .requireMessage()
    }

    suspend fun updateGroupAvatar(groupId: Int, avatarUrl: String) {
        apiClientFactory.createClients()
            .groups
            .updateGroupAvatar(
                request = UpdateGroupAvatarRequest.newBuilder()
                    .setGroupId(groupId)
                    .setAvatarUrl(avatarUrl)
                    .build(),
                headers = headers.current()
            )
            .requireMessage()
    }

    suspend fun updateGroupDescription(groupId: Int, description: String) {
        apiClientFactory.createClients()
            .groups
            .updateGroupDescription(
                request = UpdateGroupDescriptionRequest.newBuilder()
                    .setGroupId(groupId)
                    .setDescription(description)
                    .build(),
                headers = headers.current()
            )
            .requireMessage()
    }

    suspend fun inviteMembers(groupId: Int, memberIds: List<Int>) {
        if (memberIds.isEmpty()) return
        apiClientFactory.createClients()
            .groups
            .inviteMembers(
                request = InviteMembersRequest.newBuilder()
                    .setGroupId(groupId)
                    .addAllMemberIds(memberIds)
                    .build(),
                headers = headers.current()
            )
            .requireMessage()
    }

    suspend fun removeMember(groupId: Int, targetId: Int) {
        apiClientFactory.createClients()
            .groups
            .removeMember(
                request = RemoveMemberRequest.newBuilder()
                    .setGroupId(groupId)
                    .setTargetId(targetId)
                    .build(),
                headers = headers.current()
            )
            .requireMessage()
    }

    private suspend fun getGroupDetail(groupId: Int) =
        apiClientFactory.createClients()
            .groups
            .getGroupInfo(
                request = GetGroupInfoRequest.newBuilder()
                    .setGroupId(groupId)
                    .build(),
                headers = headers.current()
            )
            .requireMessage()
}

private fun GroupInfo.toData(): GroupData =
    GroupData(
        groupId = groupId,
        name = name,
        avatarUrl = avatarUrl,
        description = description,
        ownerId = ownerId,
        status = status.number
    )
