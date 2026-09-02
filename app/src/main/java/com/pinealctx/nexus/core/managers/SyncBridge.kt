package com.pinealctx.nexus.core.managers

import com.pinealctx.nexus.client.GatewayClient
import com.pinealctx.nexus.client.SyncEngine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncBridge @Inject constructor(
    private val gatewayClient: GatewayClient,
    private val syncEngine: SyncEngine
) {
    suspend fun startSync() {
        syncEngine.fetchDifference()
        gatewayClient.connect()
    }

    suspend fun syncOnce() {
        syncEngine.fetchDifference()
    }

    fun stopSync() {
        gatewayClient.disconnect()
    }

    suspend fun coldStart(): Long = syncEngine.coldStart().toLong()

    fun getLocalSn(): Long = syncEngine.getLocalSn().toLong()

    fun clearLocalData() {
        syncEngine.clearLocalData()
    }

    fun resetSyncedData() {
        syncEngine.resetSyncedData()
    }
}
