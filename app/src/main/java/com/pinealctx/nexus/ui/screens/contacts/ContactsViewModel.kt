package com.pinealctx.nexus.ui.screens.contacts

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinealctx.nexus.core.AppEventBus
import com.pinealctx.nexus.core.ContactData
import com.pinealctx.nexus.core.GroupData
import com.pinealctx.nexus.core.NexusError
import com.pinealctx.nexus.core.managers.ContactManager
import com.pinealctx.nexus.core.managers.GroupManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import uniffi.nexus_ffi.ConnectionStatus
import javax.inject.Inject

data class ContactsUiState(
    val contacts: List<ContactData> = emptyList(),
    val groups: List<GroupData> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val contactManager: ContactManager,
    private val groupManager: GroupManager,
    private val appEventBus: AppEventBus
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContactsUiState())
    val uiState: StateFlow<ContactsUiState> = _uiState.asStateFlow()

    init {
        loadDirectory(fetchIfEmpty = true)
        observeUpdates()
    }

    private fun observeUpdates() {
        appEventBus.contactsUpdated()
            .onEach { loadDirectory() }
            .launchIn(viewModelScope)
        appEventBus.conversationsUpdated()
            .onEach { loadDirectory() }
            .launchIn(viewModelScope)
        appEventBus.connectionStatus
            .filter { it == ConnectionStatus.CONNECTED }
            .onEach { loadDirectory(fetchIfEmpty = true) }
            .launchIn(viewModelScope)
    }

    private fun loadDirectory(fetchIfEmpty: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                var contacts = contactManager.getContacts()
                var groups = groupManager.listGroups()
                Log.i("Contacts", "Loaded directory: contacts=${contacts.size}, groups=${groups.size}")
                if (fetchIfEmpty && (contacts.isEmpty() || groups.isEmpty())) {
                    try {
                        if (contacts.isEmpty()) {
                            contactManager.fetchContacts()
                            contacts = contactManager.getContacts()
                        }
                        if (groups.isEmpty()) {
                            groupManager.fetchGroups()
                            groups = groupManager.listGroups()
                        }
                        Log.i("Contacts", "Fetched directory: contacts=${contacts.size}, groups=${groups.size}")
                    } catch (e: Exception) {
                        if (e.requiresRelogin()) return@launch
                        Log.w("Contacts", "Remote directory fetch failed", e)
                    }
                }
                _uiState.value = ContactsUiState(contacts = contacts, groups = groups)
            } catch (e: Exception) {
                if (e.requiresRelogin()) return@launch
                _uiState.value = ContactsUiState(error = e.message)
            }
        }
    }

    fun refresh() {
        loadDirectory(fetchIfEmpty = true)
    }

    private fun Exception.requiresRelogin(): Boolean {
        if (!NexusError.requiresRelogin(this)) return false
        appEventBus.emitForceLogout(message ?: "Authentication expired")
        return true
    }
}
