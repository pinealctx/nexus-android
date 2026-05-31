package com.pinealctx.nexus.ui.screens.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pinealctx.nexus.core.EventBridge
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import uniffi.nexus_ffi.ConnectionStatus
import javax.inject.Inject

@HiltViewModel
class ConnectionViewModel @Inject constructor(
    eventBridge: EventBridge
) : ViewModel() {

    val connectionStatus: StateFlow<ConnectionStatus> = eventBridge.connectionStatus
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ConnectionStatus.DISCONNECTED
        )
}
