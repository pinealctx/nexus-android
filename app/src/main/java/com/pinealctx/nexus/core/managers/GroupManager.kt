package com.pinealctx.nexus.core.managers

import com.pinealctx.nexus.client.GroupApi
import com.pinealctx.nexus.core.GroupData
import com.pinealctx.nexus.core.GroupMemberData
import com.pinealctx.nexus.local.LocalDataStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking

@Singleton
class GroupManager @Inject constructor(
    private val groupApi: GroupApi,
    private val localDataStore: LocalDataStore
) {
    fun listGroups(): List<GroupData> {
        val cached = localDataStore.listGroups()
        if (cached.isNotEmpty()) return cached

        return runBlocking {
            groupApi.listGroups()
                .also { localDataStore.upsertGroups(it) }
        }
    }

    fun fetchGroups() {
        runBlocking {
            groupApi.listGroups()
                .also { localDataStore.upsertGroups(it) }
        }
    }

    fun getGroupInfo(groupId: Int): GroupData? {
        return localDataStore.getGroup(groupId)
            ?: runBlocking {
                groupApi.getGroupInfo(groupId)
                    .also { localDataStore.upsertGroup(it) }
            }
    }

    fun getGroupMembers(groupId: Int): List<GroupMemberData> {
        val cached = localDataStore.listGroupMembers(groupId)
        if (cached.isNotEmpty()) return cached

        return runBlocking {
            groupApi.getGroupMembers(groupId)
                .also { localDataStore.replaceGroupMembers(groupId, it) }
        }
    }

    fun createGroup(name: String, memberIds: List<Int>): Int {
        val groupId = runBlocking { groupApi.createGroup(name, memberIds) }
        runBlocking {
            groupApi.getGroupInfo(groupId)
                .also { localDataStore.upsertGroup(it) }
        }
        return groupId
    }

    fun dissolveGroup(groupId: Int) {
        runBlocking { groupApi.dissolveGroup(groupId) }
        localDataStore.deleteGroup(groupId)
    }

    fun leaveGroup(groupId: Int) {
        runBlocking { groupApi.leaveGroup(groupId) }
        localDataStore.deleteGroup(groupId)
    }

    fun updateGroupName(groupId: Int, name: String) {
        runBlocking { groupApi.updateGroupName(groupId, name) }
        localDataStore.updateGroupName(groupId, name)
    }

    fun updateGroupAvatar(groupId: Int, avatarUrl: String) {
        runBlocking { groupApi.updateGroupAvatar(groupId, avatarUrl) }
        localDataStore.updateGroupAvatar(groupId, avatarUrl)
    }

    fun updateGroupDescription(groupId: Int, description: String) {
        runBlocking { groupApi.updateGroupDescription(groupId, description) }
        localDataStore.updateGroupDescription(groupId, description)
    }

    fun inviteMembers(groupId: Int, memberIds: List<Int>) {
        runBlocking { groupApi.inviteMembers(groupId, memberIds) }
        runBlocking {
            groupApi.getGroupMembers(groupId)
                .also { localDataStore.replaceGroupMembers(groupId, it) }
        }
    }

    fun removeMember(groupId: Int, targetId: Int) {
        runBlocking { groupApi.removeMember(groupId, targetId) }
        localDataStore.removeGroupMember(groupId, targetId)
    }
}
