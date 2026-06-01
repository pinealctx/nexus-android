package com.pinealctx.nexus.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinealctx.nexus.core.DeviceData
import com.pinealctx.nexus.core.managers.UserManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DevicesUiState(
    val devices: List<DeviceData> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val userManager: UserManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(DevicesUiState())
    val uiState: StateFlow<DevicesUiState> = _uiState.asStateFlow()

    init {
        loadDevices()
    }

    fun loadDevices() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val devices = userManager.listDevices()
                _uiState.value = DevicesUiState(devices = devices)
            } catch (e: Exception) {
                _uiState.value = DevicesUiState(error = e.message)
            }
        }
    }

    fun removeDevice(deviceId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                userManager.removeDevice(deviceId)
                loadDevices()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    onBack: () -> Unit,
    viewModel: DevicesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var deviceToRemove by remember { mutableStateOf<DeviceData?>(null) }

    if (deviceToRemove != null) {
        AlertDialog(
            onDismissRequest = { deviceToRemove = null },
            title = { Text("Remove Device") },
            text = { Text("Remove \"${deviceToRemove?.deviceName}\" from your account? It will be logged out.") },
            confirmButton = {
                TextButton(onClick = {
                    deviceToRemove?.let { viewModel.removeDevice(it.deviceId) }
                    deviceToRemove = null
                }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deviceToRemove = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Devices") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.devices.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("No devices found")
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding)
                ) {
                    items(uiState.devices) { device ->
                        DeviceItem(
                            device = device,
                            onRemove = { deviceToRemove = device }
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceItem(device: DeviceData, onRemove: () -> Unit) {
    val icon = when (device.deviceType) {
        1 -> Icons.Filled.PhoneIphone
        2 -> Icons.Filled.PhoneAndroid
        3 -> Icons.Filled.Language
        4 -> Icons.Filled.Computer
        5 -> Icons.Filled.Terminal
        else -> Icons.Filled.Devices
    }

    ListItem(
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(device.deviceName.ifEmpty { device.deviceModel })
                if (device.isCurrent) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "Current",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        supportingContent = {
            Text("${device.osVersion} · ${device.appVersion}")
        },
        leadingContent = {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        },
        trailingContent = {
            if (!device.isCurrent) {
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    )
}
