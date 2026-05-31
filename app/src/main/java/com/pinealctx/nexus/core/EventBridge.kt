package com.pinealctx.nexus.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import uniffi.nexus_ffi.ConnectionStatus
import uniffi.nexus_ffi.EventListener
import javax.inject.Inject
import javax.inject.Singleton

data class TokenRefreshEvent(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Int
)

@Singleton
class EventBridge @Inject constructor() : EventListener {

    private val _connectionStatus = MutableSharedFlow<ConnectionStatus>(
        replay = 1,
        extraBufferCapacity = 8
    )
    val connectionStatus: SharedFlow<ConnectionStatus> = _connectionStatus.asSharedFlow()

    private val _forceLogout = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val forceLogout: SharedFlow<String> = _forceLogout.asSharedFlow()

    private val _coldStartRequired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val coldStartRequired: SharedFlow<Unit> = _coldStartRequired.asSharedFlow()

    private val _conversationsUpdated = MutableSharedFlow<Unit>(extraBufferCapacity = 64)
    val conversationsUpdated: SharedFlow<Unit> = _conversationsUpdated.asSharedFlow()

    private val _messagesUpdated = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val messagesUpdated: SharedFlow<String> = _messagesUpdated.asSharedFlow()

    private val _contactsUpdated = MutableSharedFlow<Unit>(extraBufferCapacity = 64)
    val contactsUpdated: SharedFlow<Unit> = _contactsUpdated.asSharedFlow()

    private val _tokenRefreshed = MutableSharedFlow<TokenRefreshEvent>(extraBufferCapacity = 1)
    val tokenRefreshed: SharedFlow<TokenRefreshEvent> = _tokenRefreshed.asSharedFlow()

    // EventListener callbacks — invoked from Rust tokio thread, must not block.

    override fun onConnectionStatusChanged(status: ConnectionStatus) {
        _connectionStatus.tryEmit(status)
    }

    override fun onForceLogout(reason: String) {
        _forceLogout.tryEmit(reason)
    }

    override fun onColdStartRequired() {
        _coldStartRequired.tryEmit(Unit)
    }

    override fun onConversationsUpdated() {
        _conversationsUpdated.tryEmit(Unit)
    }

    override fun onMessagesUpdated(conversationId: String) {
        _messagesUpdated.tryEmit(conversationId)
    }

    override fun onContactsUpdated() {
        _contactsUpdated.tryEmit(Unit)
    }

    override fun onTokenRefreshed(accessToken: String, refreshToken: String, expiresIn: Int) {
        _tokenRefreshed.tryEmit(TokenRefreshEvent(accessToken, refreshToken, expiresIn))
    }
}
