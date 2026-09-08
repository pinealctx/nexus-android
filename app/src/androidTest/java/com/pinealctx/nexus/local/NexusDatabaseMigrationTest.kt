package com.pinealctx.nexus.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class NexusDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        databaseClass = NexusDatabase::class.java
    )

    @Test
    fun migrate7To8() {
        helper.createDatabase(DatabaseName, 7).close()
        helper.runMigrationsAndValidate(
            DatabaseName,
            8,
            true,
            NexusDatabase.MIGRATION_7_8
        ).close()
    }

    @Test
    fun migrate8To9() {
        helper.createDatabase(DatabaseName, 8).close()
        helper.runMigrationsAndValidate(
            DatabaseName,
            9,
            true,
            NexusDatabase.MIGRATION_8_9
        ).close()
    }

    private companion object {
        const val DatabaseName = "nexus-migration-test"
    }

    @Test
    fun migrate9To10PreservesExistingText() {
        helper.createDatabase(DatabaseName, 9).apply {
            execSQL("INSERT INTO messages (conversation_id, message_id, sender_id, content_kind, text, created_at, edited, recalled) VALUES ('100', 1, 7, 'text', 'existing', 1, 0, 0)")
            close()
        }
        helper.runMigrationsAndValidate(DatabaseName, 10, true, NexusDatabase.MIGRATION_9_10).use { db ->
            db.query("SELECT text, text_entities FROM messages WHERE message_id = 1").use { cursor ->
                org.junit.Assert.assertTrue(cursor.moveToFirst())
                org.junit.Assert.assertEquals("existing", cursor.getString(0))
                org.junit.Assert.assertTrue(cursor.isNull(1))
            }
        }
    }
}
