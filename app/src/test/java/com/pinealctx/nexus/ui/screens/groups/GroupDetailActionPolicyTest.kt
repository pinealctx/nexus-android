package com.pinealctx.nexus.ui.screens.groups

import com.pinealctx.nexus.core.GroupData
import com.pinealctx.nexus.core.GroupMemberData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupDetailActionPolicyTest {
    @Test
    fun `owner dissolves group`() {
        val action = GroupDetailActionPolicy.exitAction(
            group = group(ownerId = 42),
            currentUserId = 42
        )

        assertEquals(GroupExitAction.DISSOLVE, action)
    }

    @Test
    fun `member leaves group`() {
        val action = GroupDetailActionPolicy.exitAction(
            group = group(ownerId = 42),
            currentUserId = 7
        )

        assertEquals(GroupExitAction.LEAVE, action)
    }

    @Test
    fun `missing current user falls back to leave`() {
        val action = GroupDetailActionPolicy.exitAction(
            group = group(ownerId = 42),
            currentUserId = 0
        )

        assertEquals(GroupExitAction.LEAVE, action)
    }

    @Test
    fun `missing group falls back to leave`() {
        val action = GroupDetailActionPolicy.exitAction(
            group = null,
            currentUserId = 42
        )

        assertEquals(GroupExitAction.LEAVE, action)
    }

    @Test
    fun `owner can invite members`() {
        assertTrue(GroupDetailActionPolicy.canInviteMembers(group(ownerId = 42), currentUserId = 42))
    }

    @Test
    fun `member cannot invite members`() {
        assertFalse(GroupDetailActionPolicy.canInviteMembers(group(ownerId = 42), currentUserId = 7))
    }

    @Test
    fun `owner can edit group info`() {
        assertTrue(GroupDetailActionPolicy.canEditGroupInfo(group(ownerId = 42), currentUserId = 42))
    }

    @Test
    fun `member cannot edit group info`() {
        assertFalse(GroupDetailActionPolicy.canEditGroupInfo(group(ownerId = 42), currentUserId = 7))
    }

    @Test
    fun `owner can remove regular member`() {
        val canRemove = GroupDetailActionPolicy.canRemoveMember(
            group = group(ownerId = 42),
            currentUserId = 42,
            member = member(userId = 7, role = 0)
        )

        assertTrue(canRemove)
    }

    @Test
    fun `owner cannot remove self`() {
        val canRemove = GroupDetailActionPolicy.canRemoveMember(
            group = group(ownerId = 42),
            currentUserId = 42,
            member = member(userId = 42, role = 1)
        )

        assertFalse(canRemove)
    }

    @Test
    fun `member cannot remove other member`() {
        val canRemove = GroupDetailActionPolicy.canRemoveMember(
            group = group(ownerId = 42),
            currentUserId = 7,
            member = member(userId = 8, role = 0)
        )

        assertFalse(canRemove)
    }

    @Test
    fun `owner cannot remove owner member record`() {
        val canRemove = GroupDetailActionPolicy.canRemoveMember(
            group = group(ownerId = 42),
            currentUserId = 42,
            member = member(userId = 99, role = 1)
        )

        assertFalse(canRemove)
    }

    private fun group(ownerId: Int): GroupData {
        return GroupData(
            groupId = 1,
            name = "Nexus",
            avatarUrl = "",
            description = "",
            ownerId = ownerId,
            status = 0
        )
    }

    private fun member(userId: Int, role: Int): GroupMemberData {
        return GroupMemberData(
            userId = userId,
            role = role,
            joinedAt = 1_700_000_000_000,
            displayName = "User $userId"
        )
    }
}
