package com.pinealctx.nexus.core.managers

import com.pinealctx.nexus.client.GroupApi
import com.pinealctx.nexus.core.GroupData
import com.pinealctx.nexus.core.GroupMemberData
import com.pinealctx.nexus.local.LocalDataStore
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupManager @Inject constructor(
    private val groupApi: GroupApi,
    private val localDataStore: LocalDataStore
) {
    fun observeGroups(): Flow<List<GroupData>> = localDataStore.observeGroups()

    suspend fun listGroups(): List<GroupData> {
        val cached = localDataStore.listGroups()
        if (cached.isNotEmpty()) return cached

        return groupApi.listGroups()
            .also { localDataStore.upsertGroups(it) }
    }

    suspend fun fetchGroups() {
        groupApi.listGroups().also { localDataStore.upsertGroups(it) }
    }

    suspend fun getGroupInfo(groupId: Int): GroupData? {
        return localDataStore.getGroup(groupId)
            ?: groupApi.getGroupInfo(groupId)
                .also { localDataStore.upsertGroup(it) }
    }

    suspend fun getGroupMembers(groupId: Int): List<GroupMemberData> {
        val cached = localDataStore.listGroupMembers(groupId)
        if (cached.isNotEmpty()) return cached

        return groupApi.getGroupMembers(groupId)
            .also { localDataStore.replaceGroupMembers(groupId, it) }
    }

    suspend fun fetchGroupMembers(groupId: Int): List<GroupMemberData> =
        groupApi.getGroupMembers(groupId).also { localDataStore.replaceGroupMembers(groupId, it) }

    suspend fun createGroup(name: String, memberIds: List<Int>): Int {
        val groupId = groupApi.createGroup(name, memberIds)
        groupApi.getGroupInfo(groupId).also { localDataStore.upsertGroup(it) }
        return groupId
    }

    suspend fun dissolveGroup(groupId: Int) {
        groupApi.dissolveGroup(groupId)
        localDataStore.deleteGroup(groupId)
    }

    suspend fun leaveGroup(groupId: Int) {
        groupApi.leaveGroup(groupId)
        localDataStore.deleteGroup(groupId)
    }

    suspend fun updateGroupName(groupId: Int, name: String) {
        groupApi.updateGroupName(groupId, name)
        localDataStore.updateGroupName(groupId, name)
    }

    suspend fun updateGroupAvatar(groupId: Int, avatarUrl: String) {
        groupApi.updateGroupAvatar(groupId, avatarUrl)
        localDataStore.updateGroupAvatar(groupId, avatarUrl)
    }

    suspend fun updateGroupDescription(groupId: Int, description: String) {
        groupApi.updateGroupDescription(groupId, description)
        localDataStore.updateGroupDescription(groupId, description)
    }

    suspend fun inviteMembers(groupId: Int, memberIds: List<Int>) {
        groupApi.inviteMembers(groupId, memberIds)
        groupApi.getGroupMembers(groupId)
            .also { localDataStore.replaceGroupMembers(groupId, it) }
    }

    suspend fun removeMember(groupId: Int, targetId: Int) {
        groupApi.removeMember(groupId, targetId)
        localDataStore.removeGroupMember(groupId, targetId)
    }
}
