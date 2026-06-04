package com.pinealctx.nexus.ui.screens.groups

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pinealctx.nexus.R
import com.pinealctx.nexus.core.GroupMemberData
import com.pinealctx.nexus.ui.components.InviteMembersDialog
import com.pinealctx.nexus.ui.components.NexusAvatar
import com.pinealctx.nexus.ui.components.NexusAvatarBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    onBack: () -> Unit,
    viewModel: GroupDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showExitDialog by remember { mutableStateOf(false) }
    var showInviteDialog by remember { mutableStateOf(false) }
    var showEditInfoDialog by remember { mutableStateOf(false) }
    var memberToRemove by remember { mutableStateOf<GroupMemberData?>(null) }
    val exitAction = GroupDetailActionPolicy.exitAction(uiState.group, uiState.currentUserId)
    val canInviteMembers = GroupDetailActionPolicy.canInviteMembers(uiState.group, uiState.currentUserId)
    val canEditGroupInfo = GroupDetailActionPolicy.canEditGroupInfo(uiState.group, uiState.currentUserId)
    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val resolver = context.contentResolver
        val contentType = resolver.getType(uri) ?: "image/jpeg"
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
        viewModel.updateGroupAvatar(
            data = bytes,
            fileName = groupAvatarFileName(contentType),
            contentType = contentType
        )
    }

    LaunchedEffect(uiState.leftGroup) {
        if (uiState.leftGroup) {
            onBack()
        }
    }

    if (showExitDialog) {
        val isDissolve = exitAction == GroupExitAction.DISSOLVE
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = {
                Text(
                    stringResource(
                        if (isDissolve) R.string.group_dissolve_confirm_title
                        else R.string.group_leave_confirm_title
                    )
                )
            },
            text = {
                Text(
                    stringResource(
                        if (isDissolve) R.string.group_dissolve_confirm_message
                        else R.string.group_leave_confirm_message
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    viewModel.exitGroup()
                }) {
                    Text(
                        stringResource(if (isDissolve) R.string.group_dissolve else R.string.group_leave),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showInviteDialog) {
        InviteMembersDialog(
            contacts = uiState.contacts,
            existingMemberIds = uiState.members.mapTo(mutableSetOf()) { it.userId },
            isInviting = uiState.isInviting,
            onDismiss = { showInviteDialog = false },
            onInvite = { memberIds ->
                showInviteDialog = false
                viewModel.inviteMembers(memberIds)
            }
        )
    }

    if (showEditInfoDialog && uiState.group != null) {
        EditGroupInfoDialog(
            initialName = uiState.group?.name.orEmpty(),
            initialDescription = uiState.group?.description.orEmpty(),
            isSavingName = uiState.isSavingName,
            isSavingDescription = uiState.isSavingDescription,
            onDismiss = { showEditInfoDialog = false },
            onSaveName = { viewModel.updateGroupName(it) },
            onSaveDescription = { viewModel.updateGroupDescription(it) }
        )
    }

    memberToRemove?.let { member ->
        val memberDisplayName = if (member.displayName.isBlank()) {
            stringResource(R.string.group_user_fallback, member.userId)
        } else {
            member.displayName
        }
        AlertDialog(
            onDismissRequest = { memberToRemove = null },
            title = { Text(stringResource(R.string.group_remove_member_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.group_remove_member_confirm_message,
                        memberDisplayName
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    memberToRemove = null
                    viewModel.removeMember(member)
                }) {
                    Text(
                        stringResource(R.string.group_remove_member),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { memberToRemove = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.group?.name ?: stringResource(R.string.group_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (canInviteMembers) {
                        IconButton(
                            onClick = { showInviteDialog = true },
                            enabled = !uiState.isInviting
                        ) {
                            Icon(Icons.Filled.PersonAdd, contentDescription = stringResource(R.string.invite_title))
                        }
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = uiState.error ?: stringResource(R.string.error_unknown),
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { viewModel.loadGroupDetail() }) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding)
                ) {
                    item {
                        GroupInfoHeader(
                            groupId = uiState.group?.groupId ?: 0,
                            name = uiState.group?.name ?: "",
                            description = uiState.group?.description ?: "",
                            avatarUrl = uiState.group?.avatarUrl ?: "",
                            canEdit = canEditGroupInfo,
                            isSavingAvatar = uiState.isSavingAvatar,
                            onAvatarClick = {
                                avatarPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onEdit = { showEditInfoDialog = true }
                        )
                    }

                    item {
                        Text(
                            text = stringResource(R.string.group_members, uiState.members.size),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    items(uiState.members) { member ->
                        MemberItem(
                            member = member,
                            canRemove = GroupDetailActionPolicy.canRemoveMember(
                                group = uiState.group,
                                currentUserId = uiState.currentUserId,
                                member = member
                            ),
                            isRemoving = member.userId in uiState.removingMemberIds,
                            onRemove = { memberToRemove = member }
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                    }

                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { showExitDialog = true },
                            enabled = !uiState.isExiting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                stringResource(
                                    if (uiState.isExiting) {
                                        R.string.group_exit_processing
                                    } else if (exitAction == GroupExitAction.DISSOLVE) {
                                        R.string.group_dissolve
                                    } else {
                                        R.string.group_leave
                                    }
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupInfoHeader(
    groupId: Int,
    name: String,
    description: String,
    avatarUrl: String,
    canEdit: Boolean,
    isSavingAvatar: Boolean,
    onAvatarClick: () -> Unit,
    onEdit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            NexusAvatar(
                id = groupId,
                name = name,
                avatarUrl = avatarUrl,
                size = 80.dp,
                badge = NexusAvatarBadge.Group,
                modifier = if (canEdit && !isSavingAvatar) {
                    Modifier.clickable(onClick = onAvatarClick)
                } else {
                    Modifier
                }
            )
            if (isSavingAvatar) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
            } else if (canEdit) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .size(28.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = stringResource(R.string.group_update_avatar),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = name, style = MaterialTheme.typography.headlineSmall)
            if (canEdit) {
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.group_edit_info))
                }
            }
        }
        if (description.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()
    }
}

@Composable
private fun EditGroupInfoDialog(
    initialName: String,
    initialDescription: String,
    isSavingName: Boolean,
    isSavingDescription: Boolean,
    onDismiss: () -> Unit,
    onSaveName: (String) -> Unit,
    onSaveDescription: (String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var description by remember(initialDescription) { mutableStateOf(initialDescription) }
    val trimmedName = name.trim()
    val trimmedDescription = description.trim()
    val canSaveName = trimmedName.isNotEmpty() && trimmedName != initialName && !isSavingName
    val canSaveDescription = trimmedDescription != initialDescription && !isSavingDescription

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.group_edit_info)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.group_name)) },
                    singleLine = true,
                    enabled = !isSavingName,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { onSaveName(trimmedName) },
                        enabled = canSaveName
                    ) {
                        Text(
                            stringResource(
                                if (isSavingName) R.string.saving
                                else R.string.save
                            )
                        )
                    }
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.group_description)) },
                    minLines = 3,
                    maxLines = 5,
                    enabled = !isSavingDescription,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { onSaveDescription(trimmedDescription) },
                        enabled = canSaveDescription
                    ) {
                        Text(
                            stringResource(
                                if (isSavingDescription) R.string.saving
                                else R.string.save
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.confirm))
            }
        }
    )
}

private fun groupAvatarFileName(contentType: String): String {
    val extension = when (contentType.lowercase()) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        else -> "jpg"
    }
    return "group-avatar-${System.currentTimeMillis()}.$extension"
}

@Composable
private fun MemberItem(
    member: GroupMemberData,
    canRemove: Boolean,
    isRemoving: Boolean,
    onRemove: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(member.displayName.ifEmpty { stringResource(R.string.group_user_fallback, member.userId) })
        },
        supportingContent = {
            val roleText = when (member.role) {
                1 -> stringResource(R.string.group_role_owner)
                2 -> stringResource(R.string.group_role_admin)
                else -> stringResource(R.string.group_role_member)
            }
            Text(roleText)
        },
        leadingContent = {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Person, contentDescription = null)
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (member.role == 1 || member.role == 2) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = stringResource(R.string.group_role_admin),
                        tint = if (member.role == 1) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                if (canRemove) {
                    IconButton(
                        onClick = onRemove,
                        enabled = !isRemoving,
                        modifier = Modifier.size(40.dp)
                    ) {
                        if (isRemoving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.group_remove_member),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    )
}
