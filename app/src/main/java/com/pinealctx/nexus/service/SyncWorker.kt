package com.pinealctx.nexus.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pinealctx.nexus.core.SecureStorage
import com.pinealctx.nexus.core.managers.SyncBridge
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class SyncWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val dependencies = EntryPointAccessors.fromApplication(
            applicationContext,
            SyncWorkerDependencies::class.java
        )
        if (!dependencies.secureStorage().hasTokens()) return Result.success()

        return runCatching {
            val syncBridge = dependencies.syncBridge()
            if (syncBridge.getLocalSn() == 0L) syncBridge.coldStart() else syncBridge.syncOnce()
            Result.success()
        }.getOrElse { Result.retry() }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SyncWorkerDependencies {
    fun secureStorage(): SecureStorage
    fun syncBridge(): SyncBridge
}
