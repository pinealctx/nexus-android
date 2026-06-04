package com.pinealctx.nexus.core

sealed class NexusError {
    abstract val message: String

    data class Network(
        override val message: String,
        val code: Int = 0,
        val isRetryable: Boolean = true
    ) : NexusError()

    data class Auth(
        override val message: String,
        val code: Int = 0,
        val requiresRelogin: Boolean = false
    ) : NexusError()

    data class Business(
        override val message: String,
        val code: Int = 0
    ) : NexusError()

    data class Storage(
        override val message: String,
        val code: Int = 0
    ) : NexusError()

    data class Internal(
        override val message: String
    ) : NexusError()

    companion object {
        fun from(exception: Exception): NexusError = when (exception) {
            else -> Internal(message = exception.message ?: "Unknown error")
        }

        fun requiresRelogin(exception: Exception): Boolean {
            val error = from(exception)
            return error is Auth && error.requiresRelogin
        }
    }
}

sealed class NexusResult<out T> {
    data class Success<T>(val data: T) : NexusResult<T>()
    data class Failure(val error: NexusError) : NexusResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Failure -> null
    }

    fun errorOrNull(): NexusError? = when (this) {
        is Success -> null
        is Failure -> error
    }

    inline fun <R> map(transform: (T) -> R): NexusResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Failure -> this
    }

    inline fun onSuccess(action: (T) -> Unit): NexusResult<T> {
        if (this is Success) action(data)
        return this
    }

    inline fun onFailure(action: (NexusError) -> Unit): NexusResult<T> {
        if (this is Failure) action(error)
        return this
    }
}

inline fun <T> runCatching(block: () -> T): NexusResult<T> = try {
    NexusResult.Success(block())
} catch (e: Exception) {
    NexusResult.Failure(NexusError.from(e))
}
