package com.pinealctx.nexus.core

import java.net.URI

object EndpointUrls {
    fun normalizeApiBaseUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim().trimEnd('/')
        val uri = parseUri(trimmed, "Server address")
        val scheme = uri.scheme?.lowercase()
        require(scheme == "http" || scheme == "https") {
            "Server address must start with http:// or https://"
        }
        require(!uri.host.isNullOrBlank()) {
            "Server address must include a host"
        }
        require(uri.rawQuery == null && uri.rawFragment == null) {
            "Server address must not include query or fragment"
        }
        return trimmed
    }

    fun deriveWsUrl(apiBaseUrl: String): String {
        val normalized = normalizeApiBaseUrl(apiBaseUrl)
        val uri = parseUri(normalized, "Server address")
        val wsScheme = when (uri.scheme?.lowercase()) {
            "http" -> "ws"
            "https" -> "wss"
            else -> throw IllegalArgumentException("Server address must start with http:// or https://")
        }
        val path = uri.rawPath?.trimEnd('/')?.takeIf { it.isNotBlank() } ?: ""
        return URI(
            wsScheme,
            uri.rawUserInfo,
            uri.host,
            uri.port,
            "$path/ws",
            null,
            null
        ).toString()
    }

    fun normalizeWsUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim().trimEnd('/')
        val uri = parseUri(trimmed, "Gateway address")
        val scheme = uri.scheme?.lowercase()
        require(scheme == "ws" || scheme == "wss") {
            "Gateway address must start with ws:// or wss://"
        }
        require(!uri.host.isNullOrBlank()) {
            "Gateway address must include a host"
        }
        require(uri.rawQuery == null && uri.rawFragment == null) {
            "Gateway address must not include query or fragment"
        }
        return trimmed
    }

    private fun parseUri(value: String, label: String): URI {
        return runCatching { URI(value) }
            .getOrElse { error ->
                throw IllegalArgumentException("$label must be a valid URL", error)
            }
    }
}
