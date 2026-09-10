package com.pinealctx.nexus.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pinealctx.nexus.core.managers.MessageManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class MessageSendWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val clientMessageId = inputData.getLong(CLIENT_MESSAGE_ID, 0L)
        if (clientMessageId <= 0L) return Result.failure()
        val dependencies = EntryPointAccessors.fromApplication(
            applicationContext,
            MessageSendWorkerDependencies::class.java
        )
        if (!dependencies.notifications().matchesSession(inputData.getInt("recipient_id", 0), inputData.getString("session").orEmpty())) return Result.success()
        return try {
            dependencies.messageManager().retryLocalMessage(clientMessageId)
            Result.success()
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (_: IllegalArgumentException) {
            Result.failure()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val CLIENT_MESSAGE_ID = "client_message_id"
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MessageSendWorkerDependencies {
    fun messageManager(): MessageManager
    fun notifications(): com.pinealctx.nexus.util.NotificationHelper
}
