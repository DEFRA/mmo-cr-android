package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

// Runs each migration's SQL on plain in-memory SQLite; SQLCipher is native-only, so full-schema
// row preservation stays in the androidTest `CatchRecordMigrationTest`.
@RunWith(RobolectricTestRunner::class)
class CatchRecordMigrationsJvmTests {
    private fun createDb(
        startVersion: Int,
        createStatements: List<String>,
    ): SupportSQLiteDatabase {
        val configuration =
            SupportSQLiteOpenHelper.Configuration
                .builder(RuntimeEnvironment.getApplication())
                .name(null)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(startVersion) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            createStatements.forEach { db.execSQL(it) }
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = Unit
                    },
                ).build()
        return FrameworkSQLiteOpenHelperFactory().create(configuration).writableDatabase
    }

    @Test
    fun `MIGRATION_1_2 adds numberOfShots and confirmedUsedOnTrip with a safe default`() {
        val db =
            createDb(
                1,
                listOf(
                    "CREATE TABLE catch_record_gear_use (id TEXT PRIMARY KEY NOT NULL)",
                    "INSERT INTO catch_record_gear_use (id) VALUES ('g1')",
                ),
            )

        CatchRecordMigrations.MIGRATION_1_2.migrate(db)

        db.query("SELECT numberOfShots, confirmedUsedOnTrip FROM catch_record_gear_use WHERE id = 'g1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("numberOfShots")))
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("confirmedUsedOnTrip")))
        }
        db.close()
    }

    @Test
    fun `MIGRATION_2_3 renames statRectangleId to statisticalSubRectangleCode preserving the value`() {
        val db =
            createDb(
                2,
                listOf(
                    "CREATE TABLE catch_record_gear_use (id TEXT PRIMARY KEY NOT NULL, statRectangleId TEXT)",
                    "INSERT INTO catch_record_gear_use (id, statRectangleId) VALUES ('g1', '38E95')",
                ),
            )

        CatchRecordMigrations.MIGRATION_2_3.migrate(db)

        db.query("SELECT statisticalSubRectangleCode FROM catch_record_gear_use WHERE id = 'g1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("38E95", cursor.getString(cursor.getColumnIndexOrThrow("statisticalSubRectangleCode")))
        }
        db.close()
    }

    @Test
    fun `MIGRATION_3_4 adds notLandedStraightAway and remaps species weight columns marking rows confirmed`() {
        val db =
            createDb(
                3,
                listOf(
                    "CREATE TABLE catch_record_draft (id TEXT PRIMARY KEY NOT NULL)",
                    "CREATE TABLE catch_record_gear_use (id TEXT PRIMARY KEY NOT NULL)",
                    "INSERT INTO catch_record_gear_use (id) VALUES ('g1')",
                    "CREATE TABLE catch_record_species_weight (id TEXT PRIMARY KEY NOT NULL, " +
                        "gearUseId TEXT NOT NULL, speciesId TEXT NOT NULL, retainedAboveMcrsKg REAL NOT NULL, " +
                        "retainedBelowMcrsKg REAL NOT NULL, discardedKg REAL NOT NULL)",
                    "INSERT INTO catch_record_species_weight " +
                        "(id, gearUseId, speciesId, retainedAboveMcrsKg, retainedBelowMcrsKg, discardedKg) " +
                        "VALUES ('sw1', 'g1', 'species-cod', 1.5, 0.5, 0.2)",
                ),
            )

        CatchRecordMigrations.MIGRATION_3_4.migrate(db)

        db.query("SELECT notLandedStraightAway FROM catch_record_draft").use { cursor ->
            assertTrue("table must still have zero rows, only the new column", !cursor.moveToFirst())
        }
        db
            .query(
                "SELECT speciesId, weightAboveMinimumSizeKg, weightBelowMinimumSizeKg, " +
                    "weightLegallyDiscardedKg, confirmedCaught FROM catch_record_species_weight WHERE id = 'sw1'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("species-cod", cursor.getString(cursor.getColumnIndexOrThrow("speciesId")))
                assertEquals(1.5, cursor.getDouble(cursor.getColumnIndexOrThrow("weightAboveMinimumSizeKg")), 0.0001)
                assertEquals(0.5, cursor.getDouble(cursor.getColumnIndexOrThrow("weightBelowMinimumSizeKg")), 0.0001)
                assertEquals(0.2, cursor.getDouble(cursor.getColumnIndexOrThrow("weightLegallyDiscardedKg")), 0.0001)
                assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("confirmedCaught")))
            }

        db.execSQL(
            "INSERT INTO catch_record_not_landed_species (draftId, speciesId) VALUES ('d1', 'species-plaice')",
        )
        db.query("SELECT draftId, speciesId FROM catch_record_not_landed_species").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("d1", cursor.getString(cursor.getColumnIndexOrThrow("draftId")))
        }
        db.close()
    }

    @Test
    fun `MIGRATION_4_5 adds catchRecordReference and defaults lateSubmissionWarningAcknowledged to false`() {
        val db =
            createDb(
                4,
                listOf(
                    "CREATE TABLE catch_record_draft (id TEXT PRIMARY KEY NOT NULL)",
                    "INSERT INTO catch_record_draft (id) VALUES ('d1')",
                ),
            )

        CatchRecordMigrations.MIGRATION_4_5.migrate(db)

        db
            .query(
                "SELECT catchRecordReference, lateSubmissionWarningAcknowledged FROM catch_record_draft " +
                    "WHERE id = 'd1'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("catchRecordReference")))
                assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("lateSubmissionWarningAcknowledged")))
            }
        db.close()
    }

    @Suppress("LongMethod")
    @Test
    fun `MIGRATION_5_6 dedupes active drafts per vessel keeping the most recently modified then enforces uniqueness`() {
        val db =
            createDb(
                5,
                listOf(
                    "CREATE TABLE catch_record_draft (id TEXT PRIMARY KEY NOT NULL, vesselId TEXT NOT NULL, " +
                        "status TEXT NOT NULL, modifiedAtEpochMillis INTEGER NOT NULL)",
                    // Vessel A: two active drafts, differing modifiedAt -- the newer one must be kept.
                    "INSERT INTO catch_record_draft VALUES ('a1', 'vessel-a', 'Draft', 100)",
                    "INSERT INTO catch_record_draft VALUES ('a2', 'vessel-a', 'Draft', 200)",
                    // Vessel B: two active drafts with a modifiedAt tie -- the higher id wins the tie-break.
                    "INSERT INTO catch_record_draft VALUES ('b1', 'vessel-b', 'ReadyToSubmit', 100)",
                    "INSERT INTO catch_record_draft VALUES ('b2', 'vessel-b', 'Draft', 100)",
                    // Vessel C: a single active draft -- must be left untouched (no-op case).
                    "INSERT INTO catch_record_draft VALUES ('c1', 'vessel-c', 'Draft', 50)",
                    // Vessel D: two terminal/inactive drafts -- dedupe must never touch inactive statuses.
                    "INSERT INTO catch_record_draft VALUES ('d1', 'vessel-d', 'Submitted', 10)",
                    "INSERT INTO catch_record_draft VALUES ('d2', 'vessel-d', 'Submitted', 20)",
                ),
            )

        CatchRecordMigrations.MIGRATION_5_6.migrate(db)

        fun statusOf(id: String): String =
            db.query("SELECT status FROM catch_record_draft WHERE id = ?", arrayOf(id)).use { cursor ->
                cursor.moveToFirst()
                cursor.getString(cursor.getColumnIndexOrThrow("status"))
            }

        assertEquals("Discarded", statusOf("a1"))
        assertEquals("Draft", statusOf("a2"))
        assertEquals("Discarded", statusOf("b1"))
        assertEquals("Draft", statusOf("b2"))
        assertEquals("Draft", statusOf("c1"))
        assertEquals("Submitted", statusOf("d1"))
        assertEquals("Submitted", statusOf("d2"))

        assertThrows(SQLiteConstraintException::class.java) {
            db.execSQL(
                "INSERT INTO catch_record_draft VALUES ('c2', 'vessel-c', 'Draft', 60)",
            )
        }
        db.close()
    }

    @Test
    fun `ALL contains every migration in ascending version order`() {
        val versions = CatchRecordMigrations.ALL.map { it.startVersion to it.endVersion }

        assertEquals(
            listOf(1 to 2, 2 to 3, 3 to 4, 4 to 5, 5 to 6, 6 to 7, 7 to 8),
            versions,
        )
    }
}
