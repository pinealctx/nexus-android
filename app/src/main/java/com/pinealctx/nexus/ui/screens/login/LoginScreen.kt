package com.pinealctx.nexus.ui.screens.login

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.pinealctx.nexus.R

private val LoginCardShape = RoundedCornerShape(26.dp)
private val LoginFieldShape = RoundedCornerShape(16.dp)
private val CountryCodes = listOf("+86", "+1", "+44", "+81", "+82", "+65", "+61")

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

    LoginContent(
        uiState = uiState,
        onLogoClick = ::handleLogoClick,
        onRequestCode = viewModel::requestCode,
        onPasswordLogin = viewModel::loginWithPassword,
        onToggleMethod = viewModel::toggleLoginMethod,
        onVerify = viewModel::verifyCode,
        onBack = viewModel::goBack,
        onResend = viewModel::resendCode,
        onClearError = viewModel::clearError,
        onOpenServerConfig = viewModel::showServerConfig
    )

    if (uiState.showServerConfig) {
        ServerConfigSheet(
            uiState = uiState,
            onSave = viewModel::saveServerApiBaseUrl,
            onReset = viewModel::resetServerConfig,
            onDismiss = viewModel::hideServerConfig
        )
    }
}

@Composable
internal fun LoginContent(
    uiState: LoginUiState,
    onLogoClick: () -> Unit,
    onRequestCode: (String) -> Unit,
    onPasswordLogin: (String, String) -> Unit,
    onToggleMethod: () -> Unit,
    onVerify: (String) -> Unit,
    onBack: () -> Unit,
    onResend: () -> Unit,
    onClearError: () -> Unit,
    onOpenServerConfig: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.statusBars
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            LoginBackdrop()
            val compact = maxHeight < 720.dp
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = if (compact) 20.dp else 46.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LoginHeader(compact = compact, onLogoClick = onLogoClick)
                Spacer(modifier = Modifier.height(if (compact) 24.dp else 34.dp))
                LoginPanel(
                    uiState = uiState,
                    onRequestCode = onRequestCode,
                    onPasswordLogin = onPasswordLogin,
                    onToggleMethod = onToggleMethod,
                    onVerify = onVerify,
                    onBack = onBack,
                    onResend = onResend,
                    onClearError = onClearError,
                    onOpenServerConfig = onOpenServerConfig
                )
                Spacer(modifier = Modifier.height(20.dp))
                LoginTrustFooter()
            }
        }
    }
}

@Composable
private fun LoginBackdrop() {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val background = MaterialTheme.colorScheme.background

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
    ) {
        drawCircle(
            color = primary.copy(alpha = 0.11f),
            radius = size.minDimension * 0.62f,
            center = Offset(size.width * 1.02f, size.height * 0.02f)
        )
        drawCircle(
            color = tertiary.copy(alpha = 0.07f),
            radius = size.minDimension * 0.48f,
            center = Offset(-size.width * 0.08f, size.height * 0.78f)
        )

        val nodes = listOf(
            Offset(size.width * 0.08f, size.height * 0.20f),
            Offset(size.width * 0.28f, size.height * 0.10f),
            Offset(size.width * 0.47f, size.height * 0.19f),
            Offset(size.width * 0.73f, size.height * 0.08f),
            Offset(size.width * 0.91f, size.height * 0.22f)
        )
        nodes.zipWithNext().forEach { (start, end) ->
            drawLine(
                color = primary.copy(alpha = 0.08f),
                start = start,
                end = end,
                strokeWidth = 1.dp.toPx()
            )
        }
        nodes.forEach { node ->
            drawCircle(primary.copy(alpha = 0.14f), radius = 2.dp.toPx(), center = node)
        }
    }
}

@Composable
private fun LoginHeader(compact: Boolean, onLogoClick: () -> Unit) {
    Column(
        modifier = Modifier.widthIn(max = 440.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier
                .size(if (compact) 68.dp else 78.dp)
                .clip(RoundedCornerShape(if (compact) 20.dp else 23.dp))
                .clickable(onClick = onLogoClick),
            shape = RoundedCornerShape(if (compact) 20.dp else 23.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
            shadowElevation = 12.dp
        ) {
            Image(
                painter = painterResource(R.drawable.ic_nexus_logo),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.padding(7.dp)
            )
        }
        Spacer(modifier = Modifier.height(if (compact) 14.dp else 18.dp))
        Text(
            text = stringResource(R.string.login_welcome_title),
            style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = stringResource(R.string.login_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun LoginPanel(
    uiState: LoginUiState,
    onRequestCode: (String) -> Unit,
    onPasswordLogin: (String, String) -> Unit,
    onToggleMethod: () -> Unit,
    onVerify: (String) -> Unit,
    onBack: () -> Unit,
    onResend: () -> Unit,
    onClearError: () -> Unit,
    onOpenServerConfig: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 440.dp),
        shape = LoginCardShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.72f)),
        shadowElevation = 10.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp)) {
            AnimatedContent(targetState = uiState.step, label = "login-step") { step ->
                when {
                    uiState.configLoading -> LoadingConfigState()
                    uiState.configLoaded && !uiState.phoneEnabled && !uiState.emailEnabled ->
                        NoLoginMethodState(onOpenServerConfig = onOpenServerConfig)
                    step == LoginStep.INPUT_IDENTITY -> IdentityInputStep(
                        uiState = uiState,
                        onRequestCode = onRequestCode,
                        onPasswordLogin = onPasswordLogin,
                        onToggleMethod = onToggleMethod,
                        onClearError = onClearError
                    )
                    else -> CodeInputStep(
                        uiState = uiState,
                        onVerify = onVerify,
                        onBack = onBack,
                        onResend = onResend,
                        onClearError = onClearError
                    )
                }
            }

            AnimatedVisibility(
                visible = uiState.error != null,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 }
            ) {
                Column {
                    Spacer(modifier = Modifier.height(14.dp))
                    StatusMessage(text = uiState.error.orEmpty())
                }
            }
        }
    }
}

@Composable
private fun IdentityInputStep(
    uiState: LoginUiState,
    onRequestCode: (String) -> Unit,
    onPasswordLogin: (String, String) -> Unit,
    onToggleMethod: () -> Unit,
    onClearError: () -> Unit
) {
    var phone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var countryCode by rememberSaveable { mutableStateOf("+86") }
    var password by rememberSaveable { mutableStateOf("") }
    var usePassword by rememberSaveable { mutableStateOf(false) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val passwordFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    val useEmail = uiState.emailEnabled && (uiState.useEmail || !uiState.phoneEnabled)
    val identityValue = if (useEmail) email.trim() else "$countryCode${phone.trim()}"
    val inputReady = if (useEmail) isValidEmailIdentity(email) else isValidPhoneIdentity(phone)
    val submit = {
        focusManager.clearFocus()
        if (usePassword) onPasswordLogin(identityValue, password) else onRequestCode(identityValue)
    }

    Column {
        Text(
            text = stringResource(R.string.login_form_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = stringResource(R.string.login_form_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (uiState.phoneEnabled && uiState.emailEnabled) {
            Spacer(modifier = Modifier.height(20.dp))
            LoginMethodTabs(useEmail = useEmail, onToggleMethod = onToggleMethod)
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (useEmail) {
            LoginTextField(
                value = email,
                onValueChange = {
                    email = it.trimStart().take(128)
                    onClearError()
                },
                label = stringResource(R.string.login_email),
                placeholder = stringResource(R.string.login_email_placeholder),
                leadingIcon = {
                    Icon(Icons.Outlined.Email, contentDescription = null, modifier = Modifier.size(20.dp))
                },
                trailingIcon = clearInputIcon(email, stringResource(R.string.login_clear_input)) {
                    email = ""
                    onClearError()
                },
                enabled = !uiState.isLoading,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = if (usePassword) ImeAction.Next else ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onNext = { passwordFocusRequester.requestFocus() },
                    onDone = { if (inputReady) submit() }
                )
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CountryCodePicker(
                    value = countryCode,
                    onValueChange = {
                        countryCode = it
                        onClearError()
                    },
                    enabled = !uiState.isLoading
                )
                LoginTextField(
                    value = phone,
                    onValueChange = {
                        phone = it.filter(Char::isDigit).take(20)
                        onClearError()
                    },
                    label = stringResource(R.string.login_phone),
                    placeholder = stringResource(R.string.login_phone_placeholder),
                    leadingIcon = {
                        Icon(Icons.Outlined.Phone, contentDescription = null, modifier = Modifier.size(20.dp))
                    },
                    trailingIcon = clearInputIcon(phone, stringResource(R.string.login_clear_input)) {
                        phone = ""
                        onClearError()
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isLoading,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = if (usePassword) ImeAction.Next else ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { passwordFocusRequester.requestFocus() },
                        onDone = { if (inputReady) submit() }
                    )
                )
            }
        }

        AnimatedVisibility(visible = usePassword) {
            Column {
                Spacer(modifier = Modifier.height(12.dp))
                LoginTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        onClearError()
                    },
                    label = stringResource(R.string.login_password),
                    placeholder = stringResource(R.string.login_password_placeholder),
                    leadingIcon = {
                        Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(20.dp))
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) {
                                    Icons.Outlined.VisibilityOff
                                } else {
                                    Icons.Outlined.Visibility
                                },
                                contentDescription = stringResource(
                                    if (passwordVisible) R.string.login_hide_password else R.string.login_show_password
                                ),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    modifier = Modifier.focusRequester(passwordFocusRequester),
                    enabled = !uiState.isLoading,
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { if (inputReady && password.isNotBlank()) submit() }
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        LoginPrimaryButton(
            text = stringResource(
                when {
                    uiState.isLoading && usePassword -> R.string.login_logging_in
                    uiState.isLoading -> R.string.login_sending_code
                    usePassword -> R.string.login_with_password
                    else -> R.string.login_send_code
                }
            ),
            loading = uiState.isLoading,
            enabled = !uiState.isLoading && inputReady && (!usePassword || password.isNotBlank()),
            onClick = submit
        )

        TextButton(
            onClick = {
                usePassword = !usePassword
                onClearError()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isLoading
        ) {
            Icon(
                imageVector = if (usePassword) Icons.Outlined.Shield else Icons.Outlined.Key,
                contentDescription = null,
                modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = stringResource(
                    if (usePassword) R.string.login_use_code else R.string.login_use_password
                )
            )
        }

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
private fun clearInputIcon(
    value: String,
    contentDescription: String,
    onClear: () -> Unit
): (@Composable () -> Unit)? = if (value.isBlank()) {
    null
} else {
    {
        IconButton(onClick = onClear) {
            Icon(
                Icons.Filled.Close,
                contentDescription = contentDescription,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun LoginTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    leadingIcon: @Composable (() -> Unit),
    enabled: Boolean,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
    modifier: Modifier = Modifier,
    trailingIcon: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = true,
        shape = LoginFieldShape,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.72f)
        )
    )
}

@Composable
private fun CountryCodePicker(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.width(92.dp)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(LoginFieldShape)
                .clickable(enabled = enabled) { expanded = true },
            shape = LoginFieldShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.72f))
        ) {
            Row(
                modifier = Modifier.padding(start = 14.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Icon(
                    Icons.Outlined.ExpandMore,
                    contentDescription = stringResource(R.string.login_select_country_code),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CountryCodes.forEach { code ->
                DropdownMenuItem(
                    text = { Text(code) },
                    onClick = {
                        onValueChange(code)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun LoginMethodTabs(useEmail: Boolean, onToggleMethod: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
    ) {
        Row(modifier = Modifier.padding(4.dp)) {
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
            .height(40.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        shadowElevation = if (selected) 2.dp else 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
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
    onResend: () -> Unit,
    onClearError: () -> Unit
) {
    var code by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, enabled = !uiState.isLoading) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.login_code_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (uiState.codeDestination.isBlank()) {
                        stringResource(R.string.login_code_desc)
                    } else {
                        stringResource(R.string.login_code_sent_to, uiState.codeDestination)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        VerificationCodeField(
            code = code,
            enabled = !uiState.isLoading,
            onCodeChange = {
                code = it
                onClearError()
            },
            onDone = {
                if (code.length == 6) {
                    focusManager.clearFocus()
                    onVerify(code)
                }
            }
        )

        Spacer(modifier = Modifier.height(20.dp))

        LoginPrimaryButton(
            text = stringResource(
                if (uiState.isLoading) R.string.login_verifying else R.string.login_verify
            ),
            loading = uiState.isLoading,
            enabled = !uiState.isLoading && code.length == 6,
            onClick = {
                focusManager.clearFocus()
                onVerify(code)
            }
        )

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (uiState.countdown > 0) {
                Text(
                    text = stringResource(R.string.login_resend_seconds, uiState.countdown),
                    modifier = Modifier.padding(vertical = 12.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                TextButton(onClick = onResend, enabled = !uiState.isLoading) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.login_resend_now))
                }
            }
        }
    }
}

@Composable
private fun VerificationCodeField(
    code: String,
    enabled: Boolean,
    onCodeChange: (String) -> Unit,
    onDone: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val primary = MaterialTheme.colorScheme.primary

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    BasicTextField(
        value = code,
        onValueChange = { if (enabled) onCodeChange(it.filter(Char::isDigit).take(6)) },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        enabled = enabled,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        singleLine = true,
        cursorBrush = SolidColor(Color.Transparent),
        textStyle = TextStyle(color = Color.Transparent),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled) {
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    repeat(6) { index ->
                        val digit = code.getOrNull(index)?.toString().orEmpty()
                        val active = enabled && index == code.length.coerceAtMost(5)
                        val filled = digit.isNotEmpty()
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .border(
                                    width = if (active) 1.5.dp else 1.dp,
                                    color = if (active) primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.72f),
                                    shape = RoundedCornerShape(14.dp)
                                ),
                            shape = RoundedCornerShape(14.dp),
                            color = if (filled) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
                            }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = digit,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
                Box(modifier = Modifier.size(1.dp)) { innerTextField() }
            }
        }
    )
}

@Composable
private fun LoginPrimaryButton(
    text: String,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(17.dp),
        enabled = enabled,
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            disabledElevation = 0.dp
        )
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(19.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(modifier = Modifier.width(9.dp))
        }
        Text(text, fontWeight = FontWeight.SemiBold)
        if (!loading) {
            Spacer(modifier = Modifier.width(7.dp))
            Icon(
                Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun LoadingConfigState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.5.dp)
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.login_loading_config),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NoLoginMethodState(onOpenServerConfig: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.errorContainer) {
            Icon(
                Icons.Outlined.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.padding(14.dp).size(24.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.login_no_method),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onOpenServerConfig,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(stringResource(R.string.login_server_address))
        }
    }
}

@Composable
private fun StatusMessage(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f),
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.16f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(9.dp))
            Text(text = text, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun LoginTrustFooter() {
    Row(
        modifier = Modifier.widthIn(max = 440.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Outlined.Shield,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.login_trust_footer),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

internal fun isValidPhoneIdentity(value: String): Boolean =
    value.length in 6..20 && value.all(Char::isDigit)

internal fun isValidEmailIdentity(value: String): Boolean {
    val trimmed = value.trim()
    if (trimmed.length !in 5..128 || trimmed.any(Char::isWhitespace)) return false
    val at = trimmed.indexOf('@')
    return at > 0 && at == trimmed.lastIndexOf('@') && at < trimmed.lastIndex - 2 &&
        trimmed.substring(at + 1).contains('.')
}

internal fun maskLoginDestination(value: String, useEmail: Boolean): String {
    if (useEmail) {
        val trimmed = value.trim()
        val at = trimmed.indexOf('@')
        if (at <= 0) return trimmed
        val visible = trimmed.substring(0, at).take(2)
        return "$visible***${trimmed.substring(at)}"
    }
    if (value.length <= 7) return value
    return "${value.take(3)} •••• ${value.takeLast(4)}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerConfigSheet(
    uiState: LoginUiState,
    onSave: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    var apiBaseUrl by remember(uiState.serverApiBaseUrl) {
        mutableStateOf(uiState.serverApiBaseUrl)
    }

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
                            ?: stringResource(
                                R.string.login_server_default,
                                uiState.defaultServerApiBaseUrl
                            )
                    )
                },
                shape = LoginFieldShape
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
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(stringResource(R.string.login_server_save))
                }
            }
        }
    }
}
