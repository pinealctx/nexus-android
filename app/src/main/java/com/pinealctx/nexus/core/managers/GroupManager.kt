package com.pinealctx.nexus.core.managers

import com.pinealctx.nexus.core.GroupData
import com.pinealctx.nexus.core.GroupMemberData
import com.pinealctx.nexus.core.NexusClientProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupManager @Inject constructor(
    private val clientProvider: NexusClientProvider
) {
    fun listGroups(): List<GroupData> {
        val groups = clientProvider.getOrNull()?.listGroups() ?: return emptyList()
        return groups.map { g ->
            GroupData(g.groupId, g.name, g.avatarUrl, g.description, g.ownerId, g.status)
        }
    }

    fun fetchGroups() { clientProvider.getOrNull()?.fetchGroups() }

    fun getGroupInfo(groupId: Int) { clientProvider.getOrNull()?.getGroupInfo(groupId) }

    fun getGroupMembers(groupId: Int): List<GroupMemberData> {
        val members = clientProvider.getOrNull()?.getGroupMembers(groupId) ?: return emptyList()
        return members.map { m ->
            GroupMemberData(m.userId, m.role, m.joinedAt, m.displayName)
        }
    }

    fun createGroup(name: String, memberIds: List<Int>): Int =
        clientProvider.get().createGroup(name, memberIds)

    fun dissolveGroup(groupId: Int) { clientProvider.getOrNull()?.dissolveGroup(groupId) }

    fun leaveGroup(groupId: Int) { clientProvider.getOrNull()?.leaveGroup(groupId) }

    fun updateGroupName(groupId: Int, name: String) { clientProvider.getOrNull()?.updateGroupName(groupId, name) }

    fun updateGroupAvatar(groupId: Int, avatarUrl: String) { clientProvider.getOrNull()?.updateGroupAvatar(groupId, avatarUrl) }

    fun updateGroupDescription(groupId: Int, description: String) { clientProvider.getOrNull()?.updateGroupDescription(groupId, description) }

    fun inviteMembers(groupId: Int, memberIds: List<Int>) { clientProvider.getOrNull()?.inviteMembers(groupId, memberIds) }

    fun removeMember(groupId: Int, targetId: Int) { clientProvider.getOrNull()?.removeMember(groupId, targetId) }
}
