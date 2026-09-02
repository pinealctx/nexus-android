package com.pinealctx.nexus.client

import com.connectrpc.ProtocolClientConfig
import com.connectrpc.extensions.GoogleJavaLiteProtobufStrategy
import com.connectrpc.impl.ProtocolClient
import com.connectrpc.okhttp.ConnectOkHttpClient
import com.pinealctx.nexus.BuildConfig
import com.pinealctx.nexus.core.SecureStorage
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import java.time.Duration as JavaDuration
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Singleton
class ApiClientFactory @Inject constructor(
    private val secureStorage: SecureStorage
) {
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(JavaDuration.ofSeconds(15))
        .readTimeout(JavaDuration.ofSeconds(120))
        .writeTimeout(JavaDuration.ofSeconds(120))
        .callTimeout(JavaDuration.ofSeconds(150))
        .build()

    fun currentConfig(): EndpointConfig {
        return EndpointConfig(
            apiBaseUrl = secureStorage.getApiBaseUrl() ?: BuildConfig.NEXUS_API_BASE_URL,
            wsUrl = secureStorage.getWsUrl() ?: BuildConfig.NEXUS_WS_URL,
            deviceId = secureStorage.getDeviceId()
        )
    }

    fun createClients(): ApiClients {
        val config = currentConfig()
        val protocolClient = ProtocolClient(
            httpClient = ConnectOkHttpClient(okHttpClient),
            config = ProtocolClientConfig(
                host = config.apiBaseUrl,
                serializationStrategy = GoogleJavaLiteProtobufStrategy(),
                ioCoroutineContext = Dispatchers.IO,
                timeoutOracle = { method -> rpcTimeoutForPath(method.path) }
            )
        )
        return ApiClients(protocolClient)
    }
}

internal fun rpcTimeoutForPath(path: String): Duration =
    if (path.contains("MediaService")) MEDIA_RPC_TIMEOUT else DEFAULT_RPC_TIMEOUT

private val DEFAULT_RPC_TIMEOUT = 30.seconds
private val MEDIA_RPC_TIMEOUT = 120.seconds
