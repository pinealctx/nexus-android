package com.pinealctx.nexus.service

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.pinealctx.nexus.core.AppPreferences
import com.pinealctx.nexus.core.SecureStorage
import com.pinealctx.nexus.local.LocalDataStore
import com.pinealctx.nexus.local.NotificationLedger
import com.pinealctx.nexus.util.NotificationHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class NotificationRenderingTest {
    @Test
    fun aggregatesDeduplicatesAndClearsOnlyReadMessages() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val secure = SecureStorage(context)
        check(!secure.hasTokens()) { "Notification tests require a dedicated logged-out emulator" }
        secure.saveTokens("instrumentation-access", "instrumentation-refresh", 3600, 7001)
        val ledger = NotificationLedger(context)
        val cache = LocalDataStore(context)
        val helper = NotificationHelper(context, AppPreferences(context), secure, cache, ledger)
        val manager = context.getSystemService(NotificationManager::class.java)
        try {
            helper.cancelAll()
            val data = mapOf("type" to "message", "recipient_id" to "7001", "session" to secure.notificationSession(), "conversation_id" to "7002", "sender_id" to "7003", "sender_name" to "Test sender", "group_name" to "Notification test", "message_type" to "1", "message" to "hello", "created_at" to "${System.currentTimeMillis()}")
            helper.process(data + ("message_id" to "1"))
            helper.process(data + ("message_id" to "2"))
            helper.process(data + ("message_id" to "2"))
            assertEquals(2, ledger.messages(7001, "7002").size)
            val notification = manager.activeNotifications.single { it.tag == "nexus_7001_7002" }.notification
            assertEquals(2, notification.actions.size)
            assertEquals(2, notification.extras.getParcelableArray(Notification.EXTRA_MESSAGES)?.size)
            helper.process(data + mapOf("type" to "read", "message_id" to "1"))
            assertEquals(listOf("2"), ledger.messages(7001, "7002").map { it["message_id"] })
            helper.process(data + mapOf("type" to "read", "message_id" to "2"))
            helper.process(data + ("message_id" to "1"))
            assertTrue(ledger.messages(7001, "7002").isEmpty())
            assertFalse(manager.activeNotifications.any { it.tag == "nexus_7001_7002" })
            assertFalse(helper.matchesSession(7002, secure.notificationSession()))
            assertFalse(helper.matchesSession(7001, "old-session"))
        } finally {
            helper.cancelAll()
            secure.clearTokens()
            cache.close()
        }
    }
}
