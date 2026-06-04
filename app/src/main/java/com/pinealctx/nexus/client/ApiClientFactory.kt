package com.pinealctx.nexus.client

import com.connectrpc.ProtocolClientConfig
import com.connectrpc.extensions.GoogleJavaLiteProtobufStrategy
import com.connectrpc.impl.ProtocolClient
import com.connectrpc.okhttp.ConnectOkHttpClient
import com.pinealctx.nexus.BuildConfig
import com.pinealctx.nexus.core.SecureStorage
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiClientFactory @Inject constructor(
    private val secureStorage: SecureStorage
) {
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(15))
        .readTimeout(Duration.ofSeconds(60))
        .writeTimeout(Duration.ofSeconds(60))
        .callTimeout(Duration.ofSeconds(90))
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
                ioCoroutineContext = Dispatchers.IO
            )
        )
        return ApiClients(protocolClient)
    }
}
