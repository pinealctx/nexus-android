package com.pinealctx.nexus.ui.screens.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinealctx.nexus.core.ContactData
import com.pinealctx.nexus.core.EventBridge
import com.pinealctx.nexus.core.NexusCoreWrapper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ContactsUiState(
    val contacts: List<ContactData> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val core: NexusCoreWrapper,
    private val eventBridge: EventBridge
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContactsUiState())
    val uiState: StateFlow<ContactsUiState> = _uiState.asStateFlow()

    init {
        loadContacts()
        observeUpdates()
    }

    private fun observeUpdates() {
        eventBridge.contactsUpdated
            .onEach { loadContacts() }
            .launchIn(viewModelScope)
    }

    private fun loadContacts() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val contacts = core.getContacts()
                _uiState.value = ContactsUiState(contacts = contacts)
            } catch (e: Exception) {
                _uiState.value = ContactsUiState(error = e.message)
            }
        }
    }
}
