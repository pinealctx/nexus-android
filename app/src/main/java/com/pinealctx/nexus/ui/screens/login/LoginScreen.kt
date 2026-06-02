package com.pinealctx.nexus.ui.screens.login

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pinealctx.nexus.R

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val logoClickTimestamps = remember { mutableStateListOf<Long>() }

    LaunchedEffect(uiState.isLoggedIn) {
        if (uiState.isLoggedIn) onLoginSuccess()
    }

    fun handleLogoClick() {
        val now = System.currentTimeMillis()
        logoClickTimestamps.removeAll { now - it > 5_000 }
        logoClickTimestamps.add(now)
        if (logoClickTimestamps.size >= 5) {
            logoClickTimestamps.clear()
            viewModel.showServerConfig()
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable { handleLogoClick() }
            ) {
                Text(
                    text = "N",
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Nexus", style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(48.dp))

            when {
                uiState.configLoading -> {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.login_loading_config),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                uiState.configLoaded && !uiState.phoneEnabled && !uiState.emailEnabled -> {
                    Text(
                        text = stringResource(R.string.login_no_method),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = { viewModel.showServerConfig() }) {
                        Text(stringResource(R.string.login_server_address))
                    }
                }
                uiState.step == LoginStep.INPUT_IDENTITY -> IdentityInputStep(
                    uiState = uiState,
                    onRequestCode = { viewModel.requestCode(it) },
                    onToggleMethod = { viewModel.toggleLoginMethod() }
                )
                uiState.step == LoginStep.INPUT_CODE -> CodeInputStep(
                    uiState = uiState,
                    onVerify = { viewModel.verifyCode(it) },
                    onBack = { viewModel.goBack() },
                    onResend = { /* handled by countdown */ }
                )
            }

            if (uiState.error != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = uiState.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    if (uiState.showServerConfig) {
        ServerConfigDialog(
            uiState = uiState,
            onSave = { viewModel.saveServerApiBaseUrl(it) },
            onReset = { viewModel.resetServerConfig() },
            onDismiss = { viewModel.hideServerConfig() }
        )
    }
}

@Composable
private fun IdentityInputStep(
    uiState: LoginUiState,
    onRequestCode: (String) -> Unit,
    onToggleMethod: () -> Unit
) {
    var identity by remember { mutableStateOf("") }

    val useEmail = uiState.emailEnabled && (uiState.useEmail || !uiState.phoneEnabled)
    val label = if (useEmail) stringResource(R.string.login_email) else stringResource(R.string.login_phone)
    val keyboardType = if (useEmail) KeyboardType.Email else KeyboardType.Phone

    OutlinedTextField(
        value = identity,
        onValueChange = { identity = it },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        enabled = !uiState.isLoading,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true
    )

    Spacer(modifier = Modifier.height(24.dp))

    Button(
        onClick = { onRequestCode(identity) },
        modifier = Modifier.fillMaxWidth(),
        enabled = !uiState.isLoading && identity.isNotBlank()
    ) {
        if (uiState.isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text(stringResource(R.string.login_send_code))
        }
    }

    // Toggle phone/email if both enabled
    if (uiState.phoneEnabled && uiState.emailEnabled) {
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onToggleMethod) {
            Text(
                if (uiState.useEmail) {
                    stringResource(R.string.login_use_phone)
                } else {
                    stringResource(R.string.login_use_email)
                }
            )
        }
    }
}

@Composable
private fun CodeInputStep(
    uiState: LoginUiState,
    onVerify: (String) -> Unit,
    onBack: () -> Unit,
    onResend: () -> Unit
) {
    var code by remember { mutableStateOf("") }

    Text(
        text = stringResource(R.string.login_code_title),
        style = MaterialTheme.typography.titleMedium
    )

    Spacer(modifier = Modifier.height(24.dp))

    OutlinedTextField(
        value = code,
        onValueChange = { if (it.length <= 6) code = it },
        label = { Text(stringResource(R.string.login_code_label)) },
        modifier = Modifier.fillMaxWidth(),
        enabled = !uiState.isLoading,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true
    )

    Spacer(modifier = Modifier.height(24.dp))

    Button(
        onClick = { onVerify(code) },
        modifier = Modifier.fillMaxWidth(),
        enabled = !uiState.isLoading && code.length == 6
    ) {
        if (uiState.isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text(stringResource(R.string.login_verify))
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.back))
        }
        if (uiState.countdown > 0) {
            Text(
                text = stringResource(R.string.login_resend_seconds, uiState.countdown),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ServerConfigDialog(
    uiState: LoginUiState,
    onSave: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    var apiBaseUrl by remember(uiState.serverApiBaseUrl) { mutableStateOf(uiState.serverApiBaseUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.login_server_address)) },
        text = {
            Column {
                OutlinedTextField(
                    value = apiBaseUrl,
                    onValueChange = { apiBaseUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = uiState.serverConfigError != null,
                    placeholder = { Text(stringResource(R.string.login_server_address_hint)) },
                    supportingText = {
                        Text(
                            uiState.serverConfigError
                                ?: stringResource(
                                    R.string.login_server_default,
                                    uiState.defaultServerApiBaseUrl
                                )
                        )
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(apiBaseUrl) },
                enabled = apiBaseUrl.isNotBlank()
            ) {
                Text(stringResource(R.string.login_server_save))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onReset, enabled = uiState.isCustomServer) {
                    Text(stringResource(R.string.login_server_reset))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}
