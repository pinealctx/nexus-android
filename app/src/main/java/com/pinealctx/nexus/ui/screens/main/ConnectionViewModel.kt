package com.pinealctx.nexus.ui.screens.main

import androidx.lifecycle.ViewModel
import com.pinealctx.nexus.core.AppEventBus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import com.pinealctx.nexus.core.ConnectionStatus
import javax.inject.Inject

@HiltViewModel
class ConnectionViewModel @Inject constructor(
    appEventBus: AppEventBus
) : ViewModel() {

    val connectionStatus: StateFlow<ConnectionStatus> = appEventBus.connectionStatus
}
