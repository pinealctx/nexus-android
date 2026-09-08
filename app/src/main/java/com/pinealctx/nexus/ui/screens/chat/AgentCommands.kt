package com.pinealctx.nexus.ui.screens.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.pinealctx.nexus.R
import com.pinealctx.nexus.core.AgentCommandData

internal fun commandQuery(value: TextFieldValue): String? = value.text
    .takeIf { it.startsWith('/') && it.none(Char::isWhitespace) && value.selection.end == it.length }
    ?.removePrefix("/")

internal fun filteredAgentCommands(commands: List<AgentCommandData>, query: String): List<AgentCommandData> =
    commands.mapNotNull { item ->
        val name = item.command.trim().removePrefix("/")
        if (name.isBlank() || name.any { it.isWhitespace() || it == '/' || it.isISOControl() }) null
        else item.copy(command = "/$name")
    }.distinctBy { it.command }.filter {
        it.command.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true)
    }

internal fun insertAgentCommand(value: TextFieldValue, command: String): TextFieldValue {
    val suffix = if (value.text.startsWith('/')) {
        value.text.dropWhile { !it.isWhitespace() }.trimStart()
    } else value.text
    val prefix = "$command "
    return TextFieldValue(prefix + suffix, TextRange(prefix.length))
}

@Composable
internal fun AgentCommandList(
    commands: List<AgentCommandData>,
    query: String,
    onSelect: (AgentCommandData) -> Unit
) {
    val filtered = filteredAgentCommands(commands, query)
    Surface(tonalElevation = 3.dp) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
            if (filtered.isEmpty()) {
                item { Text(stringResource(R.string.chat_commands_empty), Modifier.padding(24.dp)) }
            }
            items(filtered, key = { it.command }) { command ->
                ListItem(
                    headlineContent = { Text(command.command, color = MaterialTheme.colorScheme.primary) },
                    supportingContent = { Text(command.description) },
                    modifier = Modifier.clickable { onSelect(command) }
                )
            }
        }
    }
}
