package com.pinealctx.nexus.core

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.pinealctx.nexus.service.SyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext context: Context
) {
    private val workManager = WorkManager.getInstance(context)
    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun schedulePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraints)
            .build()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_SYNC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun enqueueImmediateSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraints)
            .build()
        workManager.enqueueUniqueWork(
            IMMEDIATE_SYNC_WORK,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelAll() {
        workManager.cancelUniqueWork(PERIODIC_SYNC_WORK)
        workManager.cancelUniqueWork(IMMEDIATE_SYNC_WORK)
    }

    private companion object {
        const val PERIODIC_SYNC_WORK = "nexus_periodic_sync"
        const val IMMEDIATE_SYNC_WORK = "nexus_immediate_sync"
    }
}
