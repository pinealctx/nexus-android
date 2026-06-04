package com.pinealctx.nexus.core.managers

import com.pinealctx.nexus.client.GatewayClient
import com.pinealctx.nexus.client.SyncEngine
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncBridge @Inject constructor(
    private val gatewayClient: GatewayClient,
    private val syncEngine: SyncEngine
) {
    fun startSync() {
        runBlocking { syncEngine.fetchDifference() }
        gatewayClient.connect()
    }

    fun stopSync() {
        gatewayClient.disconnect()
    }

    fun coldStart(): Long = runBlocking { syncEngine.coldStart().toLong() }

    fun getLocalSn(): Long = syncEngine.getLocalSn().toLong()

    fun clearLocalData() {
        syncEngine.clearLocalData()
    }
}
