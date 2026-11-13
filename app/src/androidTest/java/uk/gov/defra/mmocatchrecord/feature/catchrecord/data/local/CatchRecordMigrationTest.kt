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
    fun migrate1To13fullChainPreservesARowAcrossEveryIntermediateSchemaChange() {
        val db = helper.createDatabase(databaseName, 1)
        db.seedV1DraftWithChildren("draft-v1", "vessel-legacy", 9_000L)

        CatchRecordMigrations.ALL.forEach { it.migrate(db) }

        db
            .query(
                "SELECT status, lateSubmissionWarningAcknowledged, catchRecordReference, createdAtEpochMillis, " +
                    "submittedAtEpochMillis, syncedAtEpochMillis, serverCatchRecordId, serverVersion, " +
                    "createIdempotencyKey, submitIdempotencyKey, submissionStep, submissionFailureReason " +
                    "FROM catch_record_draft WHERE id = ?",
                arrayOf("draft-v1"),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Draft", cursor.getString(0))
                assertEquals(0, cursor.getInt(1))
                assertTrue(cursor.isNull(2))
                assertEquals(9_000L, cursor.getLong(3))
                assertTrue(cursor.isNull(4))
                assertTrue(cursor.isNull(5))
                assertTrue("new v12 submission columns must be null, never backfilled (ADR 0022)", cursor.isNull(6))
                assertTrue(cursor.isNull(7))
                assertTrue(cursor.isNull(8))
                assertTrue(cursor.isNull(9))
                assertTrue(cursor.isNull(10))
                assertTrue(cursor.isNull(11))
            }
        db
            .query(
                "SELECT statisticalSubRectangleCode, confirmedUsedOnTrip, statisticalSubRectangleLatitude, " +
                    "statisticalSubRectangleLongitude FROM catch_record_gear_use WHERE draftId = ?",
                arrayOf("draft-v1"),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("38E95", cursor.getString(0))
                assertEquals(0, cursor.getInt(1))
                assertTrue("new v9 coordinate column must be null, never backfilled (ADR 0017)", cursor.isNull(2))
                assertTrue("new v9 coordinate column must be null, never backfilled (ADR 0017)", cursor.isNull(3))
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
        db
            .query(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'reference_data_metadata'",
            ).use { cursor -> assertTrue(cursor.moveToFirst()) }
        db.close()
    }

    /** ADR 0022: the legacy `{PREFIX}-DDMMYYYY-HHMMSS` reference is rewritten to `GBR-{PREFIX}-DDMMYY-HHMMSS`. */
    @Test
    fun migrate11To12rewritesLegacyReferencesAndAddsTheSubmissionColumns() {
        val db = helper.createDatabase(databaseName, VERSION_11)
        db.seedV11Draft("draft-legacy", "vessel-1", "B14974-27072026-150815")
        db.seedV11Draft("draft-no-reference", "vessel-2", null)
        db.seedV11Draft("draft-already-new", "vessel-3", "GBR-C99999-010126-090000")
        db.seedV11Draft("draft-non-matching", "vessel-4", "B14974-1")

        CatchRecordMigrations.MIGRATION_11_12.migrate(db)

        assertReferenceRewrittenTo(db, "draft-legacy", "GBR-B14974-270726-150815")
        assertReferenceRewrittenTo(db, "draft-already-new", "GBR-C99999-010126-090000")
        assertReferenceRewrittenTo(db, "draft-non-matching", "B14974-1")
        db.query("SELECT catchRecordReference FROM catch_record_draft WHERE id = 'draft-no-reference'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
        }
        db
            .query(
                "SELECT serverCatchRecordId, serverVersion, createIdempotencyKey, submitIdempotencyKey, " +
                    "submissionStep, submissionFailureReason FROM catch_record_draft WHERE id = 'draft-legacy'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                for (column in 0..5) assertTrue(cursor.isNull(column))
            }
        db.close()
    }

    @Test
    fun migrate11To12producesASchemaRoomItselfValidates() {
        helper.createDatabase(databaseName, VERSION_11).apply {
            seedV11Draft("draft-validated", "vessel-1", "B14974-27072026-150815")
            close()
        }

        val migrated =
            helper.runMigrationsAndValidate(databaseName, VERSION_12, true, CatchRecordMigrations.MIGRATION_11_12)

        assertReferenceRewrittenTo(migrated, "draft-validated", "GBR-B14974-270726-150815")
        migrated.close()
    }

    /** ADR 0022: a stale cached `gears:*` metadata row must be dropped so the next sync is unconditional. */
    @Test
    fun migrate12To13invalidatesCachedGearsMetadataButLeavesOtherDatasetsAlone() {
        val db = helper.createDatabase(databaseName, VERSION_12)
        db.seedV12GearWithMeasurement("gear-1", "under-10m")
        db.seedV12Metadata("gears:under-10m")
        db.seedV12Metadata("gears:all")
        db.seedV12Metadata("vessels")

        CatchRecordMigrations.MIGRATION_12_13.migrate(db)

        db.query("SELECT datasetKey FROM reference_data_metadata ORDER BY datasetKey").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("vessels", cursor.getString(0))
            assertTrue("only the non-gears metadata row must survive", !cursor.moveToNext())
        }
        db
            .query("SELECT apiMeasurementId FROM reference_data_gear_measurement WHERE gearId = 'gear-1'")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.isNull(0))
            }
        db.close()
    }

    @Test
    fun migrate12To13producesASchemaRoomItselfValidates() {
        helper.createDatabase(databaseName, VERSION_12).apply {
            seedV12GearWithMeasurement("gear-1", "under-10m")
            close()
        }

        val migrated =
            helper.runMigrationsAndValidate(databaseName, VERSION_13, true, CatchRecordMigrations.MIGRATION_12_13)

        migrated.close()
    }

    private fun SupportSQLiteDatabase.seedV12GearWithMeasurement(
        gearId: String,
        band: String,
    ) {
        execSQL(
            "INSERT INTO reference_data_gear (id, vesselLengthBand, code, name, categoryId, categoryCode, " +
                "categoryName, pairFishing, orderIndex) VALUES (?, ?, 'TBB', 'Beam trawl', 'c1', 'TRAWL_NETS', " +
                "'Trawl nets', 0, 0)",
            arrayOf(gearId, band),
        )
        execSQL(
            "INSERT INTO reference_data_gear_measurement (gearId, vesselLengthBand, key, label, type, orderIndex) " +
                "VALUES (?, ?, 'mesh_size_mm', 'Mesh size', 'Decimal', 0)",
            arrayOf(gearId, band),
        )
    }

    private fun SupportSQLiteDatabase.seedV12Metadata(datasetKey: String) =
        execSQL(
            "INSERT INTO reference_data_metadata (datasetKey, collectionId, schemaVersion, fetchedAtEpochMillis) " +
                "VALUES (?, 'col-1', '1.0', 1000)",
            arrayOf(datasetKey),
        )

    private fun assertReferenceRewrittenTo(
        db: SupportSQLiteDatabase,
        id: String,
        expected: String,
    ) = db.query("SELECT catchRecordReference FROM catch_record_draft WHERE id = ?", arrayOf(id)).use { cursor ->
        assertTrue(cursor.moveToFirst())
        assertEquals(expected, cursor.getString(0))
    }

    @Test
    fun migrate8To9addsCoordinateColumnsPreservingTheExistingCode() {
        val db = helper.createDatabase(databaseName, 8)
        db.execSQL(
            "INSERT INTO catch_record_draft (id, vesselId, status, modifiedAtEpochMillis, " +
                "lateSubmissionWarningAcknowledged) VALUES ('draft-v8', 'vessel-1', 'Draft', 1_000, 0)",
        )
        db.execSQL(
            "INSERT INTO catch_record_gear_use (id, draftId, gearTypeId, statisticalSubRectangleCode, " +
                "orderIndex, confirmedUsedOnTrip) VALUES ('draft-v8-gear', 'draft-v8', 'gear-seine-nets', " +
                "'38E95', 0, 1)",
        )

        CatchRecordMigrations.MIGRATION_8_9.migrate(db)

        db
            .query(
                "SELECT statisticalSubRectangleCode, statisticalSubRectangleLatitude, " +
                    "statisticalSubRectangleLongitude FROM catch_record_gear_use WHERE id = 'draft-v8-gear'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("38E95", cursor.getString(0))
                assertTrue(cursor.isNull(1))
                assertTrue(cursor.isNull(2))
            }
        db.close()
    }

    /** ADR 0018: the v6 partial unique index is dropped so one vessel can hold several active drafts. */
    @Test
    fun migrate10To11dropsTheActiveVesselIndexAndFreesAVesselToHoldSeveralDrafts() {
        val db = helper.createDatabase(databaseName, VERSION_10)
        db.execSQL(PARTIAL_UNIQUE_INDEX_SQL)
        db.seedV10Draft("draft-a", "vessel-shared", "Draft")
        runCatching { db.seedV10Draft("draft-b", "vessel-shared", "Draft") }.also {
            assertTrue("The v10 partial index must reject a second active draft before the migration", it.isFailure)
        }

        CatchRecordMigrations.MIGRATION_10_11.migrate(db)

        db.seedV10Draft("draft-b", "vessel-shared", "Draft")
        db.seedV10Draft("draft-c", "vessel-shared", "ReadyToSubmit")
        query(db, "SELECT COUNT(*) FROM catch_record_draft WHERE vesselId = 'vessel-shared'", 3)
        db
            .query(
                "SELECT name FROM sqlite_master WHERE type = 'index' AND name = ?",
                arrayOf("index_catch_record_draft_active_vessel"),
            ).use { cursor -> assertTrue("The index must be gone after MIGRATION_10_11", !cursor.moveToFirst()) }
        db.close()
    }

    /** Room can validate v11 because nothing raw-SQL remains for it to trip over (ADR 0018). */
    @Test
    fun migrate10To11producesASchemaRoomItselfValidates() {
        helper.createDatabase(databaseName, VERSION_10).apply {
            seedV10Draft("draft-validated", "vessel-1", "Draft")
            close()
        }

        val migrated =
            helper.runMigrationsAndValidate(databaseName, VERSION_11, true, CatchRecordMigrations.MIGRATION_10_11)

        query(migrated, "SELECT COUNT(*) FROM catch_record_draft WHERE id = 'draft-validated'", 1)
        migrated.close()
    }

    private fun query(
        db: SupportSQLiteDatabase,
        sql: String,
        expected: Int,
    ) = db.query(sql).use { cursor ->
        assertTrue(cursor.moveToFirst())
        assertEquals(sql, expected, cursor.getInt(0))
    }

    private fun SupportSQLiteDatabase.seedV10Draft(
        id: String,
        vesselId: String,
        status: String,
    ) = execSQL(
        "INSERT INTO catch_record_draft (id, vesselId, status, modifiedAtEpochMillis, createdAtEpochMillis, " +
            "lateSubmissionWarningAcknowledged) VALUES (?, ?, ?, 1000, 1000, 0)",
        arrayOf<Any>(id, vesselId, status),
    )

    private fun SupportSQLiteDatabase.seedV11Draft(
        id: String,
        vesselId: String,
        catchRecordReference: String?,
    ) = execSQL(
        "INSERT INTO catch_record_draft (id, vesselId, status, modifiedAtEpochMillis, createdAtEpochMillis, " +
            "lateSubmissionWarningAcknowledged, catchRecordReference) VALUES (?, ?, 'Draft', 1000, 1000, 0, ?)",
        arrayOf<Any?>(id, vesselId, catchRecordReference),
    )

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
        const val VERSION_10 = 10
        const val VERSION_11 = 11
        const val VERSION_12 = 12
        const val VERSION_13 = 13
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
