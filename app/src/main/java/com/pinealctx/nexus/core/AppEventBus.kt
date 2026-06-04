package com.pinealctx.nexus.core

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class ConnectionStatus {
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    DISCONNECTED
}

sealed class AppEvent {
    sealed class Connection : AppEvent() {
        object Connected : Connection()
        object Connecting : Connection()
        object Reconnecting : Connection()
        object Disconnected : Connection()
    }

    data class MessagesUpdated(val conversationId: String) : AppEvent()
    object ConversationsUpdated : AppEvent()
    object ContactsUpdated : AppEvent()
    object AgentsUpdated : AppEvent()
    data class ForceLogout(val reason: String) : AppEvent()
    object ColdStartRequired : AppEvent()
    data class TokenRefreshed(
        val accessToken: String,
        val refreshToken: String,
        val expiresIn: Int
    ) : AppEvent()
}

@Singleton
class AppEventBus @Inject constructor() {

    private val _events = MutableSharedFlow<AppEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<AppEvent> = _events.asSharedFlow()
    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    inline fun <reified T : AppEvent> on(): Flow<T> = events.filterIsInstance()

    // Convenience accessors for common event types
    fun connectionEvents(): Flow<AppEvent.Connection> = on()
    fun messagesUpdated(): Flow<AppEvent.MessagesUpdated> = on()
    fun conversationsUpdated(): Flow<AppEvent.ConversationsUpdated> = on()
    fun contactsUpdated(): Flow<AppEvent.ContactsUpdated> = on()
    fun agentsUpdated(): Flow<AppEvent.AgentsUpdated> = on()
    fun tokenRefreshed(): Flow<AppEvent.TokenRefreshed> = on()
    fun forceLogout(): Flow<AppEvent.ForceLogout> = on()
    fun coldStartRequired(): Flow<AppEvent.ColdStartRequired> = on()

    fun emitForceLogout(reason: String) {
        _events.tryEmit(AppEvent.ForceLogout(reason))
    }

    fun emitColdStartRequired() {
        _events.tryEmit(AppEvent.ColdStartRequired)
    }

    fun emitConversationsUpdated() {
        _events.tryEmit(AppEvent.ConversationsUpdated)
    }

    fun emitMessagesUpdated(conversationId: String) {
        _events.tryEmit(AppEvent.MessagesUpdated(conversationId))
    }

    fun emitContactsUpdated() {
        _events.tryEmit(AppEvent.ContactsUpdated)
    }

    fun emitAgentsUpdated() {
        _events.tryEmit(AppEvent.AgentsUpdated)
    }

    fun emitConnected() {
        onConnectionStatusChanged(ConnectionStatus.CONNECTED)
    }

    fun emitConnecting() {
        onConnectionStatusChanged(ConnectionStatus.CONNECTING)
    }

    fun emitReconnecting() {
        onConnectionStatusChanged(ConnectionStatus.RECONNECTING)
    }

    fun emitDisconnected() {
        onConnectionStatusChanged(ConnectionStatus.DISCONNECTED)
    }

    fun onConnectionStatusChanged(status: ConnectionStatus) {
        _connectionStatus.value = status
        Log.i("NexusEvent", "Connection status: $status")
        val event = when (status) {
            ConnectionStatus.CONNECTED -> AppEvent.Connection.Connected
            ConnectionStatus.CONNECTING -> AppEvent.Connection.Connecting
            ConnectionStatus.RECONNECTING -> AppEvent.Connection.Reconnecting
            ConnectionStatus.DISCONNECTED -> AppEvent.Connection.Disconnected
        }
        _events.tryEmit(event)
    }

    fun onForceLogout(reason: String) {
        _events.tryEmit(AppEvent.ForceLogout(reason))
    }

    fun onColdStartRequired() {
        _events.tryEmit(AppEvent.ColdStartRequired)
    }

    fun onConversationsUpdated() {
        _events.tryEmit(AppEvent.ConversationsUpdated)
    }

    fun onMessagesUpdated(conversationId: String) {
        _events.tryEmit(AppEvent.MessagesUpdated(conversationId))
    }

    fun onContactsUpdated() {
        _events.tryEmit(AppEvent.ContactsUpdated)
    }

    fun onAgentsUpdated() {
        _events.tryEmit(AppEvent.AgentsUpdated)
    }

    fun onTokenRefreshed(accessToken: String, refreshToken: String, expiresIn: Int) {
        _events.tryEmit(AppEvent.TokenRefreshed(accessToken, refreshToken, expiresIn))
    }
}
