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
 * JVM/Robolectric coverage for `MIGRATION_8_9`'s SQL directly against a plain (unencrypted) in-memory
 * SQLite database — SQLCipher is Android-only native code so the full-schema row-preservation coverage
 * stays in [uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local.CatchRecordMigrationTest]
 * (androidTest); this test only exercises the migration's own added-column SQL for Kover (ADR 0017).
 */
@RunWith(RobolectricTestRunner::class)
class CatchRecordMigration8To9Tests {
    @Test
    fun `MIGRATION_8_9 adds the coordinate columns leaving existing rows null`() {
        val configuration =
            SupportSQLiteOpenHelper.Configuration
                .builder(RuntimeEnvironment.getApplication())
                .name(null)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(8) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(
                                "CREATE TABLE catch_record_gear_use (id TEXT PRIMARY KEY NOT NULL, " +
                                    "statisticalSubRectangleCode TEXT)",
                            )
                            db.execSQL(
                                "INSERT INTO catch_record_gear_use (id, statisticalSubRectangleCode) " +
                                    "VALUES ('g1', '38E95')",
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

        CatchRecordMigrations.MIGRATION_8_9.migrate(db)

        db
            .query(
                "SELECT statisticalSubRectangleCode, statisticalSubRectangleLatitude, " +
                    "statisticalSubRectangleLongitude FROM catch_record_gear_use WHERE id = 'g1'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("38E95", cursor.getString(cursor.getColumnIndexOrThrow("statisticalSubRectangleCode")))
                assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("statisticalSubRectangleLatitude")))
                assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("statisticalSubRectangleLongitude")))
            }

        db.execSQL(
            "UPDATE catch_record_gear_use SET statisticalSubRectangleLatitude = 50.9, " +
                "statisticalSubRectangleLongitude = 0.6 WHERE id = 'g1'",
        )
        db
            .query(
                "SELECT statisticalSubRectangleLatitude, statisticalSubRectangleLongitude " +
                    "FROM catch_record_gear_use WHERE id = 'g1'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(
                    50.9,
                    cursor.getDouble(cursor.getColumnIndexOrThrow("statisticalSubRectangleLatitude")),
                    0.0001,
                )
                assertEquals(
                    0.6,
                    cursor.getDouble(cursor.getColumnIndexOrThrow("statisticalSubRectangleLongitude")),
                    0.0001,
                )
            }
        db.close()
    }
}
