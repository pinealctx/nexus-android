package com.pinealctx.nexus.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class NexusDatabaseMigrationTest {
    @Test
    fun migrate11To12PreservesDraftsAndCreatesReactions() {
        helper.createDatabase(DatabaseName, 11).apply {
            execSQL("INSERT INTO drafts (conversation_id,text,text_entities) VALUES ('100','draft','[]')")
            close()
        }
        helper.runMigrationsAndValidate(DatabaseName, 12, true, NexusDatabase.MIGRATION_11_12).use { db ->
            db.query("SELECT text FROM drafts WHERE conversation_id='100'").use {
                org.junit.Assert.assertTrue(it.moveToFirst())
                org.junit.Assert.assertEquals("draft", it.getString(0))
            }
            db.execSQL("INSERT INTO reaction_snapshots VALUES ('100',1,0,X'')")
            db.execSQL("INSERT INTO notification_actions VALUES (1,1)")
        }
    }
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
    fun migrate10To11PreservesMessagesAndCreatesDraftStorage() {
        helper.createDatabase(DatabaseName, 10).apply {
            execSQL("INSERT INTO messages (conversation_id, message_id, sender_id, content_kind, text, created_at, edited, recalled) VALUES ('100', 1, 7, 'text', 'existing', 1, 0, 0)")
            close()
        }
        helper.runMigrationsAndValidate(DatabaseName, 11, true, NexusDatabase.MIGRATION_10_11).use { db ->
            db.query("SELECT text FROM messages WHERE message_id = 1").use {
                org.junit.Assert.assertTrue(it.moveToFirst())
                org.junit.Assert.assertEquals("existing", it.getString(0))
            }
            db.execSQL("INSERT INTO drafts (conversation_id, text, text_entities) VALUES ('100', 'draft', '[]')")
            db.query("SELECT text FROM drafts WHERE conversation_id = '100'").use {
                org.junit.Assert.assertTrue(it.moveToFirst())
                org.junit.Assert.assertEquals("draft", it.getString(0))
            }
        }
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
