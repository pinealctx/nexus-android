package com.pinealctx.nexus.core

import android.util.Log
import com.connectrpc.Code
import com.connectrpc.ConnectException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

suspend fun <T> runBackgroundRpcWithRetry(
    label: String,
    maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    call: suspend () -> T
): T {
    require(maxAttempts > 0)
    var attempt = 1

    while (true) {
        try {
            return call()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (attempt >= maxAttempts || !error.isRetryableBackgroundRpcError()) throw error

            val delayMs = (INITIAL_RETRY_DELAY_MS shl (attempt - 1))
                .coerceAtMost(MAX_RETRY_DELAY_MS)
            Log.w(TAG, "$label failed; retrying in ${delayMs}ms (attempt ${attempt + 1}/$maxAttempts)")
            delay(delayMs)
            attempt += 1
        }
    }
}

internal fun Throwable.isRetryableBackgroundRpcError(): Boolean {
    val visited = mutableSetOf<Throwable>()
    var current: Throwable? = this

    while (current != null && visited.add(current)) {
        if (current is IOException) return true
        if (current is ConnectException) {
            if (current.code == Code.DEADLINE_EXCEEDED || current.code == Code.UNAVAILABLE) {
                return true
            }
            current = current.exception ?: current.cause
        } else {
            current = current.cause
        }
    }
    return false
}

private const val TAG = "NexusRpc"
private const val DEFAULT_MAX_ATTEMPTS = 4
private const val INITIAL_RETRY_DELAY_MS = 500L
private const val MAX_RETRY_DELAY_MS = 10_000L
