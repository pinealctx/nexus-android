package com.pinealctx.nexus.ui.screens.settings

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.pinealctx.nexus.R

private const val PREF_NAME = "nexus_settings"
private const val KEY_ALERTS = "notification_alerts"
private const val KEY_SOUND = "notification_sound"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE) }
    var alertsEnabled by remember { mutableStateOf(prefs.getBoolean(KEY_ALERTS, true)) }
    var soundEnabled by remember { mutableStateOf(prefs.getBoolean(KEY_SOUND, true)) }

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
                        checked = alertsEnabled,
                        onCheckedChange = {
                            alertsEnabled = it
                            prefs.edit().putBoolean(KEY_ALERTS, it).apply()
                        }
                    )
                }
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.notification_settings_sound)) },
                supportingContent = { Text(stringResource(R.string.notification_settings_sound_desc)) },
                trailingContent = {
                    Switch(
                        checked = soundEnabled,
                        onCheckedChange = {
                            soundEnabled = it
                            prefs.edit().putBoolean(KEY_SOUND, it).apply()
                        }
                    )
                }
            )
        }
    }
}
