package com.pinealctx.nexus.local

import android.app.Application
import com.pinealctx.nexus.core.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class NotificationLedgerTest {
    @Test fun `dedup persists across instances is scoped to account and expires`() {
        val context = RuntimeEnvironment.getApplication()
        val ledger = NotificationLedger(context)
        ledger.clear()
        assertTrue(ledger.accept(1, "event", 1))
        assertFalse(NotificationLedger(context).accept(1, "event", 2))
        assertTrue(ledger.accept(2, "event", 2))
        assertTrue(ledger.accept(1, "event", 49 * 60 * 60 * 1000L))
        ledger.markRead(1, "2", 10)
        ledger.markRead(1, "2", 5)
        assertEquals(10, NotificationLedger(context).readWaterline(1, "2"))
        assertEquals(0, ledger.readWaterline(2, "2"))
        ledger.clear()
    }
    @Test fun `notification reply insertion is atomic and never replaces sent state`() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("nexus.db")
        val store = LocalDataStore(context)
        try {
            val message = LocalMessageData(123, "2", null, 1, MessageContent.Text("reply"), null, 100, MessageSendState.SENDING)
            store.enqueueNotificationReply(message)
            store.markLocalMessageSent(123, 7)
            store.enqueueNotificationReply(message)
            assertEquals(MessageSendState.SENT, store.getLocalMessage(123)?.sendState)
            store.deleteLocalMessage(123)
            store.enqueueNotificationReply(message)
            assertNull(store.getLocalMessage(123))
        } finally { store.close(); context.deleteDatabase("nexus.db") }
    }
}
