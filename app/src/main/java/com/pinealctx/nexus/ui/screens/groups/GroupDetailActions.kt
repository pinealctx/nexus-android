package com.pinealctx.nexus.ui.screens.groups

import com.pinealctx.nexus.core.GroupData
import com.pinealctx.nexus.core.GroupMemberData

enum class GroupExitAction {
    LEAVE,
    DISSOLVE
}

object GroupDetailActionPolicy {
    fun exitAction(group: GroupData?, currentUserId: Int): GroupExitAction {
        return if (isOwner(group, currentUserId)) {
            GroupExitAction.DISSOLVE
        } else {
            GroupExitAction.LEAVE
        }
    }

    fun canInviteMembers(group: GroupData?, currentUserId: Int): Boolean {
        return isOwner(group, currentUserId)
    }

    fun canEditGroupInfo(group: GroupData?, currentUserId: Int): Boolean {
        return isOwner(group, currentUserId)
    }

    fun canRemoveMember(group: GroupData?, currentUserId: Int, member: GroupMemberData): Boolean {
        return isOwner(group, currentUserId) &&
            member.userId != currentUserId &&
            member.role != GroupMemberRoleOwner
    }

    private fun isOwner(group: GroupData?, currentUserId: Int): Boolean {
        return group != null && currentUserId > 0 && group.ownerId == currentUserId
    }

    private const val GroupMemberRoleOwner = 1
}
