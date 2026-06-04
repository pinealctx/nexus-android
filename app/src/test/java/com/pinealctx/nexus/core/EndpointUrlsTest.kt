package com.pinealctx.nexus.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EndpointUrlsTest {
    @Test
    fun `normalizes api base url by trimming whitespace and trailing slash`() {
        assertEquals(
            "https://api.example.com/v1",
            EndpointUrls.normalizeApiBaseUrl("  https://api.example.com/v1/  ")
        )
    }

    @Test
    fun `derives websocket url from http and https api base urls`() {
        assertEquals(
            "wss://api.example.com/ws",
            EndpointUrls.deriveWsUrl("https://api.example.com")
        )
        assertEquals(
            "ws://10.0.2.2:8080/api/ws",
            EndpointUrls.deriveWsUrl("http://10.0.2.2:8080/api/")
        )
    }

    @Test
    fun `normalizes discovered websocket url`() {
        assertEquals(
            "wss://api.example.com/ws",
            EndpointUrls.normalizeWsUrl(" wss://api.example.com/ws/ ")
        )
    }

    @Test
    fun `rejects invalid api and websocket urls`() {
        assertThrows(IllegalArgumentException::class.java) {
            EndpointUrls.normalizeApiBaseUrl("ftp://api.example.com")
        }
        assertThrows(IllegalArgumentException::class.java) {
            EndpointUrls.normalizeApiBaseUrl("https:///missing-host")
        }
        assertThrows(IllegalArgumentException::class.java) {
            EndpointUrls.normalizeApiBaseUrl("https://api.example.com?debug=true")
        }
        assertThrows(IllegalArgumentException::class.java) {
            EndpointUrls.normalizeWsUrl("https://api.example.com/ws")
        }
    }
}
