package com.pinealctx.nexus.ui.screens.agents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinealctx.nexus.core.AgentInfoData
import com.pinealctx.nexus.core.managers.AgentManager
import com.pinealctx.nexus.core.managers.ContactManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AgentsUiState(
    val featuredAgents: List<AgentInfoData> = emptyList(),
    val myAgents: List<AgentInfoData> = emptyList(),
    val searchResults: List<AgentInfoData> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AgentsViewModel @Inject constructor(
    private val agentManager: AgentManager,
    private val contactManager: ContactManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AgentsUiState())
    val uiState: StateFlow<AgentsUiState> = _uiState.asStateFlow()

    init {
        loadAgents()
    }

    private fun loadAgents() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val featured = agentManager.listFeaturedAgents()
                val mine = agentManager.listMyAgents()
                _uiState.value = AgentsUiState(featuredAgents = featured, myAgents = mine)
            } catch (e: Exception) {
                _uiState.value = AgentsUiState(error = e.message)
            }
        }
    }

    fun search(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(searchResults = emptyList())
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val results = contactManager.searchUsers(query)
                val agentResults = results.map { contact ->
                    val info = agentManager.getAgentInfo(contact.userId)
                    info
                }.filterNotNull()
                _uiState.value = _uiState.value.copy(searchResults = agentResults)
            } catch (_: Exception) {}
        }
    }

    fun addAgent(agentUserId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                contactManager.addContact(agentUserId)
            } catch (_: Exception) {}
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentsScreen(
    onAgentClick: (Int) -> Unit = {},
    onOpenMiniApp: (Int) -> Unit = {},
    viewModel: AgentsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Agents") })

        // Search bar
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = { viewModel.search(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search agents") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(24.dp)
        )

        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.searchQuery.isNotBlank() -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.searchResults) { agent ->
                        AgentListItem(agent = agent, onAgentClick = onAgentClick, onOpenMiniApp = onOpenMiniApp, onAdd = { viewModel.addAgent(it) })
                    }
                }
            }
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (uiState.myAgents.isNotEmpty()) {
                        item {
                            Text(
                                text = "My Agents",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        items(uiState.myAgents) { agent ->
                            AgentListItem(agent = agent, onAgentClick = onAgentClick, onOpenMiniApp = onOpenMiniApp, isMine = true)
                        }
                        item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
                    }

                    item {
                        Text(
                            text = "Featured",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    items(uiState.featuredAgents) { agent ->
                        AgentListItem(agent = agent, onAgentClick = onAgentClick, onOpenMiniApp = onOpenMiniApp, onAdd = { viewModel.addAgent(it) })
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentListItem(
    agent: AgentInfoData,
    onAgentClick: (Int) -> Unit,
    onOpenMiniApp: (Int) -> Unit,
    onAdd: ((Int) -> Unit)? = null,
    isMine: Boolean = false
) {
    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = agent.nickname.ifEmpty { agent.username },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (agent.isSystemAgent) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Text(
                            text = "System",
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        },
        supportingContent = {
            Text(
                text = agent.signature.ifEmpty { "@${agent.username}" },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.SmartToy, contentDescription = null)
                }
            }
        },
        trailingContent = {
            Row {
                if (agent.miniAppEnabled) {
                    IconButton(onClick = { onOpenMiniApp(agent.userId) }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open App", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                if (!isMine && onAdd != null) {
                    IconButton(onClick = { onAdd(agent.userId) }) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = "Add")
                    }
                }
            }
        },
        modifier = Modifier.clickable { onAgentClick(agent.userId) }
    )
}
