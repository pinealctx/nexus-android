package com.pinealctx.nexus.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinealctx.nexus.R
import com.pinealctx.nexus.core.SyncManager
import com.pinealctx.nexus.core.managers.AuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AccountSecurityUiState(
    val isSaving: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val loggedOut: Boolean = false
)

@HiltViewModel
class AccountSecurityViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val syncManager: SyncManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(AccountSecurityUiState())
    val uiState: StateFlow<AccountSecurityUiState> = _uiState.asStateFlow()

    fun setupPassword(password: String, confirmation: String) {
        if (!validate(password, confirmation)) return
        runAction { authManager.setupPassword(password) }
    }

    fun changePassword(currentPassword: String, newPassword: String, confirmation: String) {
        if (currentPassword.isBlank()) {
            _uiState.value = AccountSecurityUiState(error = "Current password is required")
            return
        }
        if (!validate(newPassword, confirmation)) return
        runAction { authManager.changePassword(currentPassword, newPassword) }
    }

    fun logoutAll() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = AccountSecurityUiState(isSaving = true)
            runCatching { authManager.logoutAll() }
                .onSuccess {
                    syncManager.stopSession()
                    _uiState.value = AccountSecurityUiState(loggedOut = true)
                }
                .onFailure { _uiState.value = AccountSecurityUiState(error = it.message) }
        }
    }

    private fun runAction(action: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = AccountSecurityUiState(isSaving = true)
            runCatching { action() }
                .onSuccess { _uiState.value = AccountSecurityUiState(message = "Password updated") }
                .onFailure { _uiState.value = AccountSecurityUiState(error = it.message) }
        }
    }

    private fun validate(password: String, confirmation: String): Boolean {
        val error = when {
            password.length < 8 -> "Password must contain at least 8 characters"
            password != confirmation -> "Passwords do not match"
            else -> null
        }
        if (error != null) _uiState.value = AccountSecurityUiState(error = error)
        return error == null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSecurityScreen(
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: AccountSecurityViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val passwordOptions = KeyboardOptions(keyboardType = KeyboardType.Password)

    LaunchedEffect(uiState.loggedOut) {
        if (uiState.loggedOut) onLoggedOut()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.account_security_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = currentPassword,
                onValueChange = { currentPassword = it },
                label = { Text(stringResource(R.string.account_current_password)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = passwordOptions,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = { Text(stringResource(R.string.account_new_password)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = passwordOptions,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = confirmation,
                onValueChange = { confirmation = it },
                label = { Text(stringResource(R.string.account_confirm_password)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = passwordOptions,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { viewModel.changePassword(currentPassword, newPassword, confirmation) },
                enabled = !uiState.isSaving,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.account_change_password)) }
            OutlinedButton(
                onClick = { viewModel.setupPassword(newPassword, confirmation) },
                enabled = !uiState.isSaving,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.account_setup_password)) }

            uiState.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            uiState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (uiState.isSaving) CircularProgressIndicator()

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = viewModel::logoutAll,
                enabled = !uiState.isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.account_logout_all), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
