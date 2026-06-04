package com.pinealctx.nexus.client

import com.connectrpc.ConnectException
import com.connectrpc.ResponseMessage

fun <T> ResponseMessage<T>.requireMessage(): T {
    var message: T? = null
    var cause: ConnectException? = null

    success {
        message = it.message
        Unit
    }
    failure {
        cause = it.cause
        Unit
    }

    cause?.let { throw it }
    return requireNotNull(message) { "Connect response did not contain a success message or failure cause." }
}
