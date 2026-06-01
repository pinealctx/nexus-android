package com.pinealctx.nexus.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinealctx.nexus.core.ProfileData
import com.pinealctx.nexus.core.SyncManager
import com.pinealctx.nexus.core.managers.AuthManager
import com.pinealctx.nexus.core.managers.UserManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val profile: ProfileData? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userManager: UserManager,
    private val authManager: AuthManager,
    private val syncManager: SyncManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    private fun loadProfile() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val profile = userManager.getMyProfile()
                _uiState.value = SettingsUiState(profile = profile)
            } catch (e: Exception) {
                _uiState.value = SettingsUiState(error = e.message)
            }
        }
    }

    fun logout() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                authManager.logout()
            } catch (_: Exception) {}
            syncManager.stopSession()
        }
    }
}
