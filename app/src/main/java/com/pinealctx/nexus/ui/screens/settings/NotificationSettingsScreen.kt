package com.pinealctx.nexus.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinealctx.nexus.R
import com.pinealctx.nexus.core.AppPreferences
import com.pinealctx.nexus.core.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val appPreferences: AppPreferences
) : ViewModel() {
    val settings: StateFlow<AppSettings> = appPreferences.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings()
    )

    fun setAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch { appPreferences.setNotificationAlerts(enabled) }
    }

    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch { appPreferences.setNotificationSound(enabled) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    viewModel: NotificationSettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notification_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.notification_settings_alerts)) },
                supportingContent = { Text(stringResource(R.string.notification_settings_alerts_desc)) },
                trailingContent = {
                    Switch(
                        checked = settings.notificationAlerts,
                        onCheckedChange = viewModel::setAlertsEnabled
                    )
                }
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.notification_settings_sound)) },
                supportingContent = { Text(stringResource(R.string.notification_settings_sound_desc)) },
                trailingContent = {
                    Switch(
                        checked = settings.notificationSound,
                        onCheckedChange = viewModel::setSoundEnabled
                    )
                }
            )
        }
    }
}
