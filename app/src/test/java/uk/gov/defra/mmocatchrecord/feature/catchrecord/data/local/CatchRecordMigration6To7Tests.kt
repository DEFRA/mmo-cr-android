package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * JVM/Robolectric coverage for `MIGRATION_6_7`'s SQL directly against a plain (unencrypted) in-memory
 * SQLite database — SQLCipher is Android-only native code so the full-schema R1-R3 row-preservation
 * coverage stays in [uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local.CatchRecordMigrationTest]
 * (androidTest); this test only exercises the migration's own added-column/backfill SQL for Kover.
 */
@RunWith(RobolectricTestRunner::class)
class CatchRecordMigration6To7Tests {
    @Test
    fun `MIGRATION_6_7 adds the audit columns and backfills createdAt from modifiedAt`() {
        val configuration =
            SupportSQLiteOpenHelper.Configuration
                .builder(RuntimeEnvironment.getApplication())
                .name(null)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(6) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(
                                "CREATE TABLE catch_record_draft (id TEXT PRIMARY KEY NOT NULL, " +
                                    "modifiedAtEpochMillis INTEGER NOT NULL)",
                            )
                            db.execSQL(
                                "INSERT INTO catch_record_draft (id, modifiedAtEpochMillis) VALUES ('d1', 1234)",
                            )
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = Unit
                    },
                ).build()
        val db = FrameworkSQLiteOpenHelperFactory().create(configuration).writableDatabase

        CatchRecordMigrations.MIGRATION_6_7.migrate(db)

        db
            .query(
                "SELECT createdAtEpochMillis, submittedAtEpochMillis, syncedAtEpochMillis " +
                    "FROM catch_record_draft WHERE id = 'd1'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1234L, cursor.getLong(cursor.getColumnIndexOrThrow("createdAtEpochMillis")))
                assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("submittedAtEpochMillis")))
                assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("syncedAtEpochMillis")))
            }
        db.close()
    }
}
