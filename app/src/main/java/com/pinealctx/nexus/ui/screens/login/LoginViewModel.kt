package com.pinealctx.nexus.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinealctx.nexus.core.SecureStorage
import com.pinealctx.nexus.core.SyncManager
import com.pinealctx.nexus.core.managers.AuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LoginStep { INPUT_IDENTITY, INPUT_CODE }

data class LoginUiState(
    val step: LoginStep = LoginStep.INPUT_IDENTITY,
    val phoneEnabled: Boolean = false,
    val emailEnabled: Boolean = false,
    val configLoading: Boolean = true,
    val configLoaded: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isLoggedIn: Boolean = false,
    val countdown: Int = 0,
    val useEmail: Boolean = false,
    val serverApiBaseUrl: String = "",
    val defaultServerApiBaseUrl: String = "",
    val isCustomServer: Boolean = false,
    val showServerConfig: Boolean = false,
    val serverConfigError: String? = null
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val secureStorage: SecureStorage,
    private val syncManager: SyncManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private var verifyToken: String = ""
    private var lastIdentityType: Int = 0
    private var lastIdentityValue: String = ""

    init {
        loadServerConfigState()
        checkExistingSession()
        loadClientConfig()
    }

    private fun checkExistingSession() {
        if (secureStorage.hasTokens() && secureStorage.getUserId() > 0) {
            _uiState.value = _uiState.value.copy(isLoggedIn = true)
        }
    }

    private fun loadClientConfig() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(configLoading = true, error = null)
            try {
                val config = authManager.getClientConfig()
                authManager.applyDiscoveredWsUrl(config.wsUrl)
                _uiState.value = _uiState.value.copy(
                    phoneEnabled = config.phoneEnabled,
                    emailEnabled = config.emailEnabled,
                    useEmail = config.emailEnabled && !config.phoneEnabled,
                    configLoading = false,
                    configLoaded = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    phoneEnabled = false,
                    emailEnabled = false,
                    configLoading = false,
                    configLoaded = true,
                    error = e.message ?: "Failed to load login config"
                )
            }
        }
    }

    private fun loadServerConfigState() {
        val config = authManager.getServerConfig()
        _uiState.value = _uiState.value.copy(
            serverApiBaseUrl = config.apiBaseUrl,
            defaultServerApiBaseUrl = config.defaultApiBaseUrl,
            isCustomServer = config.isCustom,
            serverConfigError = null
        )
    }

    fun toggleLoginMethod() {
        if (!_uiState.value.phoneEnabled || !_uiState.value.emailEnabled) return
        _uiState.value = _uiState.value.copy(
            useEmail = !_uiState.value.useEmail,
            error = null
        )
    }

    fun requestCode(identityValue: String) {
        val state = _uiState.value
        val identityType = if (state.useEmail) 1 else 2
        val methodEnabled = if (state.useEmail) state.emailEnabled else state.phoneEnabled
        if (!methodEnabled) {
            _uiState.value = state.copy(error = "Login method is not enabled")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val result = authManager.requestVerifyCode(identityType, identityValue)
                verifyToken = result.verifyToken
                lastIdentityType = identityType
                lastIdentityValue = identityValue
                _uiState.value = _uiState.value.copy(
                    step = LoginStep.INPUT_CODE,
                    isLoading = false,
                    countdown = result.expiresIn.coerceAtMost(60)
                )
                startCountdown()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to send code"
                )
            }
        }
    }

    fun resendCode() {
        val state = _uiState.value
        if (state.countdown > 0 || lastIdentityType == 0 || lastIdentityValue.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val result = authManager.requestVerifyCode(lastIdentityType, lastIdentityValue)
                verifyToken = result.verifyToken
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    countdown = result.expiresIn.coerceAtMost(60)
                )
                startCountdown()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to resend code"
                )
            }
        }
    }

    fun verifyCode(code: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val result = authManager.verifyCode(verifyToken, code)
                secureStorage.saveTokens(result.accessToken, result.refreshToken, result.expiresIn, result.userId)
                authManager.reopenForUser(result.userId)
                authManager.restoreSession(
                    result.accessToken,
                    result.refreshToken,
                    result.expiresIn,
                    result.userId
                )
                syncManager.startSession()
                _uiState.value = _uiState.value.copy(isLoading = false, isLoggedIn = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Verification failed"
                )
            }
        }
    }

    fun goBack() {
        _uiState.value = _uiState.value.copy(step = LoginStep.INPUT_IDENTITY, error = null)
    }

    fun showServerConfig() {
        loadServerConfigState()
        _uiState.value = _uiState.value.copy(showServerConfig = true, serverConfigError = null)
    }

    fun hideServerConfig() {
        _uiState.value = _uiState.value.copy(showServerConfig = false, serverConfigError = null)
    }

    fun saveServerApiBaseUrl(apiBaseUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                authManager.setServerApiBaseUrl(apiBaseUrl)
                loadServerConfigState()
                _uiState.value = _uiState.value.copy(showServerConfig = false)
                loadClientConfig()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    serverConfigError = e.message ?: "Failed to save server address"
                )
            }
        }
    }

    fun resetServerConfig() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                authManager.resetServerConfig()
                loadServerConfigState()
                _uiState.value = _uiState.value.copy(showServerConfig = false)
                loadClientConfig()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    serverConfigError = e.message ?: "Failed to reset server address"
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun startCountdown() {
        viewModelScope.launch {
            var remaining = _uiState.value.countdown
            while (remaining > 0) {
                delay(1000)
                remaining--
                _uiState.value = _uiState.value.copy(countdown = remaining)
            }
        }
    }
}
