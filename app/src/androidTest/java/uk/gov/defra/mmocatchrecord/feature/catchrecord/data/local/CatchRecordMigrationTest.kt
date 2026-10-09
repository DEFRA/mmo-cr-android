package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.security.SecureRandom

/**
 * Instrumented (SQLCipher is Android-only native code, see [CatchRecordEncryptionSmokeTest]) R1-R3 row
 * and raw-SQL index preservation coverage for `MIGRATION_6_7` — see ADR 0014 Phase C for the R2 rationale.
 */
@RunWith(AndroidJUnit4::class)
class CatchRecordMigrationTest {
    private val databaseName = "migration-test.db"
    private val passphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }

    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            CatchRecordDatabase::class.java,
            emptyList(),
            FreshPassphraseOpenHelperFactory(passphrase),
        )

    @Before
    fun loadNativeLibrary() {
        SqlCipherLibraryLoader.ensureLoaded()
    }

    // Applies the Migration directly rather than via runMigrationsAndValidate: Room's own schema
    // comparison can't model this raw-SQL partial index and spuriously fails on its presence (ADR 0014).
    @Test
    fun migrate6To7preservesEveryRowEveryChildRowAndThePartialUniqueIndex() {
        val seeded =
            listOf(
                Seed("draft-is-draft", "vessel-1", "Draft", 1_000L),
                Seed("draft-ready", "vessel-2", "ReadyToSubmit", 2_000L),
                Seed("draft-pending", "vessel-3", "PendingSync", 3_000L),
                Seed("draft-submitted", "vessel-4", "Submitted", 4_000L),
                Seed("draft-discarded", "vessel-5", "Discarded", 5_000L),
            )
        val db = helper.createDatabase(databaseName, VERSION_6)
        db.execSQL(PARTIAL_UNIQUE_INDEX_SQL)
        seeded.forEach { db.seedV6DraftWithChildren(it) }

        CatchRecordMigrations.MIGRATION_6_7.migrate(db)

        seeded.forEach { seed -> db.assertDraftSurvivedWithChildren(seed) }
        db.assertPartialUniqueIndexSurvived()
        db.close()
    }

    @Test
    fun migrate1To7fullChainPreservesARowAcrossEveryIntermediateSchemaChange() {
        val db = helper.createDatabase(databaseName, 1)
        db.seedV1DraftWithChildren("draft-v1", "vessel-legacy", 9_000L)

        CatchRecordMigrations.ALL.forEach { it.migrate(db) }

        db
            .query(
                "SELECT status, lateSubmissionWarningAcknowledged, catchRecordReference, createdAtEpochMillis, " +
                    "submittedAtEpochMillis, syncedAtEpochMillis FROM catch_record_draft WHERE id = ?",
                arrayOf("draft-v1"),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Draft", cursor.getString(0))
                assertEquals(0, cursor.getInt(1))
                assertTrue(cursor.isNull(2))
                assertEquals(9_000L, cursor.getLong(3))
                assertTrue(cursor.isNull(4))
                assertTrue(cursor.isNull(5))
            }
        db
            .query(
                "SELECT statisticalSubRectangleCode, confirmedUsedOnTrip FROM catch_record_gear_use WHERE draftId = ?",
                arrayOf("draft-v1"),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("38E95", cursor.getString(0))
                assertEquals(0, cursor.getInt(1))
            }
        db
            .query(
                "SELECT weightAboveMinimumSizeKg, weightBelowMinimumSizeKg, weightLegallyDiscardedKg, " +
                    "confirmedCaught FROM catch_record_species_weight WHERE gearUseId = ?",
                arrayOf("draft-v1-gear"),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(2.0, cursor.getDouble(0), 0.0)
                assertEquals(1.0, cursor.getDouble(1), 0.0)
                assertEquals(0.5, cursor.getDouble(2), 0.0)
                assertEquals(1, cursor.getInt(3))
            }
        db.close()
    }

    private data class Seed(
        val id: String,
        val vesselId: String,
        val status: String,
        val modifiedAt: Long,
    )

    private fun SupportSQLiteDatabase.seedV6DraftWithChildren(seed: Seed) {
        execSQL(
            "INSERT INTO catch_record_draft (id, vesselId, status, modifiedAtEpochMillis, " +
                "lateSubmissionWarningAcknowledged) VALUES (?, ?, ?, ?, 0)",
            arrayOf<Any>(seed.id, seed.vesselId, seed.status, seed.modifiedAt),
        )
        val gearUseId = "${seed.id}-gear"
        execSQL(
            "INSERT INTO catch_record_gear_use (id, draftId, gearTypeId, statisticalSubRectangleCode, " +
                "orderIndex, confirmedUsedOnTrip) VALUES (?, ?, 'gear-seine-nets', '38E95', 0, 1)",
            arrayOf(gearUseId, seed.id),
        )
        execSQL(
            "INSERT INTO catch_record_measurement (gearUseId, key, numericValue) VALUES (?, 'mesh-size', 80.0)",
            arrayOf(gearUseId),
        )
        execSQL(
            "INSERT INTO catch_record_species_weight (id, gearUseId, speciesId, weightAboveMinimumSizeKg, " +
                "confirmedCaught) VALUES (?, ?, 'species-cod', 12.5, 1)",
            arrayOf("${seed.id}-species", gearUseId),
        )
        execSQL(
            "INSERT INTO catch_record_landing_storage (draftId, entryId, key, value) " +
                "VALUES (?, 'entry-1', 'location', 'hold-a')",
            arrayOf(seed.id),
        )
        execSQL(
            "INSERT INTO catch_record_not_landed_species (draftId, speciesId, " +
                "weightAboveMinimumSizeKeptOnboardKg) VALUES (?, 'species-haddock', 3.2)",
            arrayOf(seed.id),
        )
    }

    private fun SupportSQLiteDatabase.seedV1DraftWithChildren(
        id: String,
        vesselId: String,
        modifiedAt: Long,
    ) {
        execSQL(
            "INSERT INTO catch_record_draft (id, vesselId, status, modifiedAtEpochMillis) VALUES (?, ?, 'Draft', ?)",
            arrayOf<Any>(id, vesselId, modifiedAt),
        )
        val gearUseId = "$id-gear"
        execSQL(
            "INSERT INTO catch_record_gear_use (id, draftId, gearTypeId, statRectangleId, orderIndex) " +
                "VALUES (?, ?, 'gear-seine-nets', '38E95', 0)",
            arrayOf(gearUseId, id),
        )
        execSQL(
            "INSERT INTO catch_record_species_weight (id, gearUseId, speciesId, retainedAboveMcrsKg, " +
                "retainedBelowMcrsKg, discardedKg) VALUES (?, ?, 'species-cod', 2.0, 1.0, 0.5)",
            arrayOf("$id-species", gearUseId),
        )
    }

    private fun SupportSQLiteDatabase.assertDraftSurvivedWithChildren(seed: Seed) {
        query(
            "SELECT vesselId, status, createdAtEpochMillis, submittedAtEpochMillis, syncedAtEpochMillis, " +
                "modifiedAtEpochMillis FROM catch_record_draft WHERE id = ?",
            arrayOf(seed.id),
        ).use { cursor ->
            assertTrue("Draft row '${seed.id}' must survive the migration", cursor.moveToFirst())
            assertEquals(seed.vesselId, cursor.getString(0))
            assertEquals(seed.status, cursor.getString(1))
            assertEquals(seed.modifiedAt, cursor.getLong(2))
            assertTrue(cursor.isNull(3))
            assertTrue(cursor.isNull(4))
            assertEquals(
                "createdAtEpochMillis must equal modifiedAtEpochMillis post-backfill (R1)",
                cursor.getLong(5),
                cursor.getLong(2),
            )
        }
        assertChildRowCount("catch_record_gear_use", "draftId", seed.id, 1)
        assertChildRowCount("catch_record_landing_storage", "draftId", seed.id, 1)
        assertChildRowCount("catch_record_not_landed_species", "draftId", seed.id, 1)
        assertChildRowCount("catch_record_measurement", "gearUseId", "${seed.id}-gear", 1)
        assertChildRowCount("catch_record_species_weight", "gearUseId", "${seed.id}-gear", 1)
    }

    private fun SupportSQLiteDatabase.assertChildRowCount(
        table: String,
        column: String,
        value: String,
        expected: Int,
    ) {
        query("SELECT COUNT(*) FROM $table WHERE $column = ?", arrayOf(value)).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("$table.$column='$value' row count", expected, cursor.getInt(0))
        }
    }

    private fun SupportSQLiteDatabase.assertPartialUniqueIndexSurvived() {
        query(
            "SELECT sql FROM sqlite_master WHERE type = 'index' AND name = ?",
            arrayOf("index_catch_record_draft_active_vessel"),
        ).use { cursor ->
            assertTrue("The partial unique index must still exist after MIGRATION_6_7", cursor.moveToFirst())
            val indexSql = cursor.getString(0)
            assertTrue(
                "Room's @Index cannot express a WHERE predicate; the raw SQL must still carry it: $indexSql",
                indexSql.contains("WHERE status IN ('Draft', 'ReadyToSubmit')"),
            )
        }
    }

    private companion object {
        const val VERSION_6 = 6
        const val PARTIAL_UNIQUE_INDEX_SQL =
            "CREATE UNIQUE INDEX IF NOT EXISTS index_catch_record_draft_active_vessel " +
                "ON catch_record_draft (vesselId) WHERE status IN ('Draft', 'ReadyToSubmit')"
    }
}

/**
 * Hands out a fresh passphrase copy per `create()` (R2 — [MigrationTestHelper] reopens the file at least
 * twice and SQLCipher commonly clears the passphrase array after first use; see ADR 0014 Phase C).
 */
private class FreshPassphraseOpenHelperFactory(
    private val passphrase: ByteArray,
) : SupportSQLiteOpenHelper.Factory {
    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper =
        SupportOpenHelperFactory(passphrase.copyOf()).create(configuration)
}
