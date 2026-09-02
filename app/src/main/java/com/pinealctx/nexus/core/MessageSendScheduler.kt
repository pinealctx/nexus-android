package com.pinealctx.nexus.core

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.pinealctx.nexus.local.LocalDataStore
import com.pinealctx.nexus.service.MessageSendWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageSendScheduler @Inject constructor(
    @ApplicationContext context: Context,
    private val localDataStore: LocalDataStore
) {
    private val workManager = WorkManager.getInstance(context)

    fun enqueue(clientMessageId: Long) {
        val request = OneTimeWorkRequestBuilder<MessageSendWorker>()
            .addTag(MESSAGE_SEND_WORK_TAG)
            .setInputData(Data.Builder().putLong(MessageSendWorker.CLIENT_MESSAGE_ID, clientMessageId).build())
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(
            "nexus_send_$clientMessageId",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun reschedulePending() {
        localDataStore.listPendingLocalMessageIds().forEach(::enqueue)
    }

    fun cancelAll() {
        workManager.cancelAllWorkByTag(MESSAGE_SEND_WORK_TAG)
    }

    private companion object {
        const val MESSAGE_SEND_WORK_TAG = "nexus_message_send"
    }
}
