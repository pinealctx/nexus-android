package com.pinealctx.nexus.ui.screens.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pinealctx.nexus.R

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.statusBars
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 36.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LoginHeader(onLogoClick = ::handleLogoClick)

            Spacer(modifier = Modifier.height(32.dp))

            LoginPanel(
                uiState = uiState,
                onRequestCode = { viewModel.requestCode(it) },
                onToggleMethod = { viewModel.toggleLoginMethod() },
                onVerify = { viewModel.verifyCode(it) },
                onBack = { viewModel.goBack() },
                onResend = { viewModel.resendCode() },
                onOpenServerConfig = { viewModel.showServerConfig() }
            )
        }
    }

    if (uiState.showServerConfig) {
        ServerConfigSheet(
            uiState = uiState,
            onSave = { viewModel.saveServerApiBaseUrl(it) },
            onReset = { viewModel.resetServerConfig() },
            onDismiss = { viewModel.hideServerConfig() }
        )
    }
}

@Composable
private fun LoginHeader(onLogoClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(24.dp))
                .clickable(onClick = onLogoClick),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFFF8FAFC),
            tonalElevation = 0.dp,
            shadowElevation = 6.dp
        ) {
            Image(
                painter = painterResource(R.drawable.ic_nexus_logo),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.padding(8.dp)
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = "Nexus AI",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.login_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun LoginPanel(
    uiState: LoginUiState,
    onRequestCode: (String) -> Unit,
    onToggleMethod: () -> Unit,
    onVerify: (String) -> Unit,
    onBack: () -> Unit,
    onResend: () -> Unit,
    onOpenServerConfig: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        when {
            uiState.configLoading -> LoadingConfigState()
            uiState.configLoaded && !uiState.phoneEnabled && !uiState.emailEnabled -> NoLoginMethodState(
                onOpenServerConfig = onOpenServerConfig
            )
            uiState.step == LoginStep.INPUT_IDENTITY -> IdentityInputStep(
                uiState = uiState,
                onRequestCode = onRequestCode,
                onToggleMethod = onToggleMethod
            )
            uiState.step == LoginStep.INPUT_CODE -> CodeInputStep(
                uiState = uiState,
                onVerify = onVerify,
                onBack = onBack,
                onResend = onResend
            )
        }

        if (uiState.error != null) {
            Spacer(modifier = Modifier.height(14.dp))
            StatusMessage(
                text = uiState.error,
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        }

        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.login_auto_create),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun IdentityInputStep(
    uiState: LoginUiState,
    onRequestCode: (String) -> Unit,
    onToggleMethod: () -> Unit
) {
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var countryCode by remember { mutableStateOf("+86") }

    val useEmail = uiState.emailEnabled && (uiState.useEmail || !uiState.phoneEnabled)
    val title = if (useEmail) stringResource(R.string.login_email_login) else stringResource(R.string.login_phone_login)
    val description = if (useEmail) stringResource(R.string.login_email_desc) else stringResource(R.string.login_phone_desc)
    val identityValue = if (useEmail) email.trim() else "${countryCode.trim()}${phone.trim()}"
    val inputReady = if (useEmail) email.trim().isNotBlank() else phone.trim().isNotBlank()

    if (uiState.phoneEnabled && uiState.emailEnabled) {
        LoginMethodTabs(useEmail = useEmail, onToggleMethod = onToggleMethod)
        Spacer(modifier = Modifier.height(22.dp))
    } else {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(20.dp))
    }

    if (useEmail) {
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(stringResource(R.string.login_email)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isLoading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true
        )
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = countryCode,
                onValueChange = { countryCode = it.take(5) },
                label = { Text(stringResource(R.string.login_country_code)) },
                modifier = Modifier.width(98.dp),
                enabled = !uiState.isLoading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true
            )
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it.filter(Char::isDigit).take(20) },
                label = { Text(stringResource(R.string.login_phone)) },
                modifier = Modifier.weight(1f),
                enabled = !uiState.isLoading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true
            )
        }
    }

    Spacer(modifier = Modifier.height(18.dp))

    Button(
        onClick = { onRequestCode(identityValue) },
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = RoundedCornerShape(8.dp),
        enabled = !uiState.isLoading && inputReady
    ) {
        if (uiState.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Text(stringResource(R.string.login_send_code))
        }
    }
}

@Composable
private fun LoginMethodTabs(useEmail: Boolean, onToggleMethod: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(modifier = Modifier.padding(3.dp)) {
            MethodTab(
                text = stringResource(R.string.login_phone_login),
                selected = !useEmail,
                modifier = Modifier.weight(1f),
                onClick = { if (useEmail) onToggleMethod() }
            )
            MethodTab(
                text = stringResource(R.string.login_email_login),
                selected = useEmail,
                modifier = Modifier.weight(1f),
                onClick = { if (!useEmail) onToggleMethod() }
            )
        }
    }
}

@Composable
private fun MethodTab(
    text: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(7.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(7.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.login_code_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(22.dp))

    VerificationCodeField(
        code = code,
        onCodeChange = { code = it }
    )

    Spacer(modifier = Modifier.height(18.dp))

    Button(
        onClick = { onVerify(code) },
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = RoundedCornerShape(8.dp),
        enabled = !uiState.isLoading && code.length == 6
    ) {
        if (uiState.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Text(stringResource(R.string.login_verify))
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.back))
        }
        if (uiState.countdown > 0) {
            Text(
                text = stringResource(R.string.login_resend_seconds, uiState.countdown),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            TextButton(onClick = onResend, enabled = !uiState.isLoading) {
                Text(stringResource(R.string.login_resend_now))
            }
        }
    }
}

@Composable
private fun VerificationCodeField(
    code: String,
    onCodeChange: (String) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val primary = MaterialTheme.colorScheme.primary

    BasicTextField(
        value = code,
        onValueChange = { onCodeChange(it.filter(Char::isDigit).take(6)) },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        singleLine = true,
        cursorBrush = SolidColor(primary),
        textStyle = TextStyle(color = Color.Transparent),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { focusRequester.requestFocus() }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(6) { index ->
                        val digit = code.getOrNull(index)?.toString() ?: ""
                        val active = index == code.length.coerceAtMost(5)
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .border(
                                    width = 1.dp,
                                    color = if (active) primary else MaterialTheme.colorScheme.outline,
                                    shape = RoundedCornerShape(8.dp)
                                ),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = digit,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
                Box(modifier = Modifier.size(1.dp)) {
                    innerTextField()
                }
            }
        }
    )
}

@Composable
private fun LoadingConfigState() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.login_loading_config),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NoLoginMethodState(onOpenServerConfig: () -> Unit) {
    StatusMessage(
        text = stringResource(R.string.login_no_method),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    )
    Spacer(modifier = Modifier.height(12.dp))
    Button(
        onClick = onOpenServerConfig,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Text(stringResource(R.string.login_server_address))
    }
}

@Composable
private fun StatusMessage(
    text: String,
    color: Color,
    contentColor: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = color,
        contentColor = contentColor
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerConfigSheet(
    uiState: LoginUiState,
    onSave: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    var apiBaseUrl by remember(uiState.serverApiBaseUrl) { mutableStateOf(uiState.serverApiBaseUrl) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.login_server_address),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.login_server_sheet_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel))
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            OutlinedTextField(
                value = apiBaseUrl,
                onValueChange = { apiBaseUrl = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = uiState.serverConfigError != null,
                label = { Text(stringResource(R.string.login_api_base_url)) },
                placeholder = { Text(stringResource(R.string.login_server_address_hint)) },
                supportingText = {
                    Text(
                        uiState.serverConfigError
                            ?: stringResource(R.string.login_server_default, uiState.defaultServerApiBaseUrl)
                    )
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.login_ws_derived),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(18.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(
                    onClick = onReset,
                    enabled = uiState.isCustomServer,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.login_server_reset))
                }
                Button(
                    onClick = { onSave(apiBaseUrl) },
                    enabled = apiBaseUrl.isNotBlank(),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.login_server_save))
                }
            }
        }
    }
}
