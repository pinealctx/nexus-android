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
}
