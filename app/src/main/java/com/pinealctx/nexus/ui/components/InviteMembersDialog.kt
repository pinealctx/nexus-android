package com.pinealctx.nexus.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pinealctx.nexus.R
import com.pinealctx.nexus.core.ContactData

@Composable
fun InviteMembersDialog(
    contacts: List<ContactData>,
    existingMemberIds: Set<Int>,
    onDismiss: () -> Unit,
    onInvite: (List<Int>) -> Unit
) {
    var selectedIds by remember { mutableStateOf(emptySet<Int>()) }
    val availableContacts = contacts.filter { it.userId !in existingMemberIds }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.invite_title)) },
        text = {
            if (availableContacts.isEmpty()) {
                Text(stringResource(R.string.invite_all_added))
            } else {
                Column {
                    Text(
                        text = stringResource(R.string.invite_selected, selectedIds.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(availableContacts) { contact ->
                            val isSelected = contact.userId in selectedIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedIds = if (isSelected) selectedIds - contact.userId
                                        else selectedIds + contact.userId
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = {
                                        selectedIds = if (isSelected) selectedIds - contact.userId
                                        else selectedIds + contact.userId
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = contact.nickname.ifEmpty { contact.username },
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "@${contact.username}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onInvite(selectedIds.toList()) },
                enabled = selectedIds.isNotEmpty()
            ) {
                Text(stringResource(R.string.invite_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
