package com.pinealctx.nexus.core

data class ServerConfigData(
    val apiBaseUrl: String,
    val wsUrl: String,
    val defaultApiBaseUrl: String,
    val defaultWsUrl: String,
    val isCustom: Boolean
)
