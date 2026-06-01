package com.pinealctx.nexus.core.managers

import com.pinealctx.nexus.core.NexusClientProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncBridge @Inject constructor(
    private val clientProvider: NexusClientProvider
) {
    fun startSync() { clientProvider.getOrNull()?.startSync() }

    fun stopSync() { clientProvider.getOrNull()?.stopSync() }

    fun coldStart(): Long = clientProvider.get().coldStart()

    fun getLocalSn(): Long = clientProvider.getOrNull()?.getLocalSn() ?: 0

    fun clearLocalData() { clientProvider.getOrNull()?.clearLocalData() }
}
