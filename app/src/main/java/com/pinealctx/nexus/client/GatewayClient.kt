package com.pinealctx.nexus.client

import android.util.Log
import com.api.v1.AuthRequest
import com.api.v1.ClientFrame
import com.api.v1.ClientFrameType
import com.api.v1.HeartbeatPing
import com.api.v1.ServerFrame
import com.api.v1.ServerFrameType
import com.pinealctx.nexus.core.AppEventBus
import com.pinealctx.nexus.core.SecureStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GatewayClient @Inject constructor(
    private val apiClientFactory: ApiClientFactory,
    private val authApi: AuthApi,
    private val deviceInfoFactory: DeviceInfoFactory,
    private val secureStorage: SecureStorage,
    private val syncEngine: SyncEngine,
    private val appEventBus: AppEventBus
) {
    private companion object {
        const val HEARTBEAT_INTERVAL_MS = 30_000L
        const val HEARTBEAT_TIMEOUT_MS = 10_000L
        const val PONG_GLOBAL_TIMEOUT_MS = 90_000L
        const val INITIAL_RECONNECT_DELAY_MS = 1_000L
        const val MAX_RECONNECT_DELAY_MS = 30_000L
        const val RECONNECT_BACKOFF = 2
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(15))
        .readTimeout(Duration.ZERO)
        .build()

    private val requestId = AtomicLong(0)
    private val intentionalClose = AtomicBoolean(false)

    @Volatile private var webSocket: WebSocket? = null
    @Volatile private var heartbeatJob: Job? = null
    @Volatile private var pongTimeoutJob: Job? = null
    @Volatile private var reconnectJob: Job? = null
    @Volatile private var reconnectCount = 0
    @Volatile private var lastPongAt = 0L
    @Volatile private var wasReconnecting = false

    fun connect() {
        val wsUrl = apiClientFactory.currentConfig().wsUrl
        intentionalClose.set(false)
        cleanupSocket()
        appEventBus.emitConnecting()
        Log.i("NexusGateway", "Connecting to $wsUrl")

        val request = Request.Builder().url(wsUrl).build()
        webSocket = okHttpClient.newWebSocket(request, Listener(wsUrl))
    }

    fun disconnect() {
        intentionalClose.set(true)
        cleanupSocket()
        reconnectCount = 0
        appEventBus.emitDisconnected()
    }

    private fun sendAuth(socket: WebSocket) {
        val token = secureStorage.getAccessToken()
        if (token.isNullOrBlank()) {
            socket.close(1008, "missing access token")
            return
        }

        val frame = ClientFrame.newBuilder()
            .setRequestId(nextRequestId())
            .setType(ClientFrameType.CLIENT_FRAME_TYPE_AUTH_REQUEST)
            .setAuthRequest(
                AuthRequest.newBuilder()
                    .setToken(token)
                    .setDeviceInfo(deviceInfoFactory.create())
                    .build()
            )
            .build()

        socket.send(frame.toByteArray().toByteString())
    }

    private fun sendPing(socket: WebSocket) {
        val now = System.currentTimeMillis()
        if (lastPongAt > 0 && now - lastPongAt > PONG_GLOBAL_TIMEOUT_MS) {
            socket.close(1001, "heartbeat timeout")
            return
        }

        val frame = ClientFrame.newBuilder()
            .setRequestId(nextRequestId())
            .setType(ClientFrameType.CLIENT_FRAME_TYPE_HEARTBEAT_PING)
            .setHeartbeatPing(HeartbeatPing.getDefaultInstance())
            .build()

        socket.send(frame.toByteArray().toByteString())
        pongTimeoutJob?.cancel()
        pongTimeoutJob = scope.launch {
            delay(HEARTBEAT_TIMEOUT_MS)
            socket.close(1001, "pong timeout")
        }
    }

    private fun handleFrame(frame: ServerFrame, socket: WebSocket) {
        when (frame.type) {
            ServerFrameType.SERVER_FRAME_TYPE_AUTH_RESPONSE -> handleAuthResponse(frame, socket)
            ServerFrameType.SERVER_FRAME_TYPE_HEARTBEAT_PONG -> {
                lastPongAt = System.currentTimeMillis()
                pongTimeoutJob?.cancel()
                pongTimeoutJob = null
            }
            ServerFrameType.SERVER_FRAME_TYPE_UPDATE -> {
                if (frame.hasUpdate()) {
                    scope.launch {
                        syncEngine.processGatewayUpdate(frame.update, isPush = true)
                    }
                }
            }
            ServerFrameType.SERVER_FRAME_TYPE_ERROR -> {
                if (frame.hasError()) {
                    Log.w("NexusGateway", "Server error: ${frame.error.error.errorName}")
                    if (frame.error.fatal) socket.close(1008, frame.error.error.errorName)
                }
            }
            ServerFrameType.SERVER_FRAME_TYPE_UNSPECIFIED,
            ServerFrameType.UNRECOGNIZED -> Unit
        }
    }

    private fun handleAuthResponse(frame: ServerFrame, socket: WebSocket) {
        if (frame.hasAuthResponse() && frame.authResponse.success) {
            val shouldFetchDifference = wasReconnecting
            wasReconnecting = false
            reconnectCount = 0
            lastPongAt = System.currentTimeMillis()
            appEventBus.emitConnected()
            startHeartbeat(socket)
            if (shouldFetchDifference) {
                scope.launch { syncEngine.fetchDifference() }
            }
            return
        }

        scope.launch {
            val refreshed = runCatching { authApi.refreshAccessToken() }.getOrDefault(false)
            if (!refreshed) {
                appEventBus.emitForceLogout("gateway auth failed")
            }
            socket.close(1008, "auth failed")
        }
    }

    private fun startHeartbeat(socket: WebSocket) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (true) {
                delay(HEARTBEAT_INTERVAL_MS)
                sendPing(socket)
            }
        }
    }

    private fun scheduleReconnect(wsUrl: String) {
        if (intentionalClose.get()) return
        appEventBus.emitReconnecting()
        wasReconnecting = true
        val delayMs = (INITIAL_RECONNECT_DELAY_MS * pow(RECONNECT_BACKOFF, reconnectCount))
            .coerceAtMost(MAX_RECONNECT_DELAY_MS)
        reconnectCount += 1
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(delayMs)
            if (!intentionalClose.get() && apiClientFactory.currentConfig().wsUrl == wsUrl) {
                connect()
            }
        }
    }

    private fun cleanupSocket() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        pongTimeoutJob?.cancel()
        pongTimeoutJob = null
        reconnectJob?.cancel()
        reconnectJob = null
        lastPongAt = 0L
        webSocket?.close(1000, null)
        webSocket = null
    }

    private fun nextRequestId(): Long = requestId.incrementAndGet()

    private fun pow(base: Int, exponent: Int): Long {
        var result = 1L
        repeat(exponent.coerceAtLeast(0)) {
            result *= base
        }
        return result
    }

    private inner class Listener(private val wsUrl: String) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            sendAuth(webSocket)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            runCatching {
                ServerFrame.parseFrom(bytes.toByteArray())
            }.onSuccess { frame ->
                handleFrame(frame, webSocket)
            }.onFailure { error ->
                Log.w("NexusGateway", "Failed to decode server frame", error)
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            heartbeatJob?.cancel()
            pongTimeoutJob?.cancel()
            if (!intentionalClose.get()) {
                scheduleReconnect(wsUrl)
            } else {
                appEventBus.emitDisconnected()
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w("NexusGateway", "WebSocket failure", t)
            heartbeatJob?.cancel()
            pongTimeoutJob?.cancel()
            scheduleReconnect(wsUrl)
        }
    }
}

