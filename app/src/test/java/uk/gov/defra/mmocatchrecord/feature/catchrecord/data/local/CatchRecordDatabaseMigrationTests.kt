package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

private const val TEST_DB_NAME = "migration-test-7-8.db"

/**
 * Builds a real v7 db file by hand (Robolectric can't see test-sourceSet schema assets for
 * `MigrationTestHelper`), migrates it via Room, and checks the draft survives and the new
 * reference-data cache tables exist (ADR 0016).
 */
@RunWith(RobolectricTestRunner::class)
class CatchRecordDatabaseMigrationTests {
    private lateinit var dbFile: File

    @Before
    fun setUp() {
        dbFile = context().getDatabasePath(TEST_DB_NAME)
        dbFile.delete()
        createV7Schema()
        insertV7Draft()
    }

    @After
    fun tearDown() {
        dbFile.delete()
    }

    @Test
    fun migrate7To8_preservesExistingDraftAndAddsReferenceDataTables() {
        val database =
            Room
                .databaseBuilder(context(), CatchRecordDatabase::class.java, dbFile.path)
                .addMigrations(CatchRecordMigrations.MIGRATION_7_8)
                .build()

        val migratedDb = database.openHelper.writableDatabase
        migratedDb
            .query(
                "SELECT vesselId, status, createdAtEpochMillis, submittedAtEpochMillis, syncedAtEpochMillis " +
                    "FROM catch_record_draft WHERE id = 'draft-1'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("vessel-1", cursor.getString(cursor.getColumnIndexOrThrow("vesselId")))
                assertEquals("Draft", cursor.getString(cursor.getColumnIndexOrThrow("status")))
                assertEquals(1000L, cursor.getLong(cursor.getColumnIndexOrThrow("createdAtEpochMillis")))
                assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("submittedAtEpochMillis")))
                assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("syncedAtEpochMillis")))
            }
        migratedDb
            .query(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'reference_data_metadata'",
            ).use { cursor -> assertTrue(cursor.moveToFirst()) }
        database.close()
    }

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun createV7Schema() {
        val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        createV7DraftAndGearUseTables(db)
        createV7MeasurementAndWeightTables(db)
        createV7StorageAndNotLandedTables(db)
        db.version = 7
        db.close()
    }

    private fun createV7DraftAndGearUseTables(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE catch_record_draft (`id` TEXT NOT NULL, `vesselId` TEXT NOT NULL, " +
                "`isTripToday` INTEGER, `departureDay` INTEGER, `departureMonth` INTEGER, " +
                "`departureYear` INTEGER, `returnDay` INTEGER, `returnMonth` INTEGER, `returnYear` INTEGER, " +
                "`departurePortId` TEXT, `departurePortSelectionMode` TEXT, `returnPortId` TEXT, " +
                "`returnPortSelectionMode` TEXT, `status` TEXT NOT NULL, `modifiedAtEpochMillis` INTEGER NOT " +
                "NULL, `notLandedStraightAway` INTEGER, `catchRecordReference` TEXT, " +
                "`lateSubmissionWarningAcknowledged` INTEGER NOT NULL, `createdAtEpochMillis` INTEGER, " +
                "`submittedAtEpochMillis` INTEGER, `syncedAtEpochMillis` INTEGER, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE INDEX index_catch_record_draft_vesselId_status ON catch_record_draft (vesselId, status)",
        )
        db.execSQL(
            "CREATE TABLE catch_record_gear_use (`id` TEXT NOT NULL, `draftId` TEXT NOT NULL, " +
                "`gearTypeId` TEXT NOT NULL, `statisticalSubRectangleCode` TEXT, `orderIndex` INTEGER NOT " +
                "NULL, `numberOfShots` INTEGER, `confirmedUsedOnTrip` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`draftId`) REFERENCES `catch_record_draft`(`id`) ON UPDATE NO ACTION ON DELETE " +
                "CASCADE)",
        )
        db.execSQL("CREATE INDEX index_catch_record_gear_use_draftId ON catch_record_gear_use (draftId)")
    }

    private fun createV7MeasurementAndWeightTables(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE catch_record_measurement (`rowId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`gearUseId` TEXT NOT NULL, `key` TEXT NOT NULL, `numericValue` REAL, `unit` TEXT, " +
                "`textValue` TEXT, FOREIGN KEY(`gearUseId`) REFERENCES `catch_record_gear_use`(`id`) ON " +
                "UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL("CREATE INDEX index_catch_record_measurement_gearUseId ON catch_record_measurement (gearUseId)")
        db.execSQL(
            "CREATE TABLE catch_record_species_weight (`id` TEXT NOT NULL, `gearUseId` TEXT NOT NULL, " +
                "`speciesId` TEXT NOT NULL, `weightAboveMinimumSizeKg` REAL, `weightBelowMinimumSizeKg` REAL, " +
                "`weightLegallyDiscardedKg` REAL, `confirmedCaught` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`gearUseId`) REFERENCES `catch_record_gear_use`(`id`) ON UPDATE NO ACTION ON " +
                "DELETE CASCADE)",
        )
        db.execSQL(
            "CREATE INDEX index_catch_record_species_weight_gearUseId " +
                "ON catch_record_species_weight (gearUseId)",
        )
    }

    private fun createV7StorageAndNotLandedTables(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE catch_record_landing_storage (`rowId` INTEGER PRIMARY KEY AUTOINCREMENT NOT " +
                "NULL, `draftId` TEXT NOT NULL, `entryId` TEXT NOT NULL, `key` TEXT NOT NULL, `value` TEXT " +
                "NOT NULL, FOREIGN KEY(`draftId`) REFERENCES `catch_record_draft`(`id`) ON UPDATE NO ACTION " +
                "ON DELETE CASCADE)",
        )
        db.execSQL(
            "CREATE INDEX index_catch_record_landing_storage_draftId ON catch_record_landing_storage (draftId)",
        )
        db.execSQL(
            "CREATE TABLE catch_record_not_landed_species (`rowId` INTEGER PRIMARY KEY AUTOINCREMENT NOT " +
                "NULL, `draftId` TEXT NOT NULL, `speciesId` TEXT NOT NULL, " +
                "`weightAboveMinimumSizeKeptOnboardKg` REAL, FOREIGN KEY(`draftId`) REFERENCES " +
                "`catch_record_draft`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL(
            "CREATE INDEX index_catch_record_not_landed_species_draftId " +
                "ON catch_record_not_landed_species (draftId)",
        )
    }

    private fun insertV7Draft() {
        val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        db.execSQL(
            "INSERT INTO catch_record_draft (id, vesselId, isTripToday, departureDay, departureMonth, " +
                "departureYear, returnDay, returnMonth, returnYear, departurePortId, " +
                "departurePortSelectionMode, returnPortId, returnPortSelectionMode, status, " +
                "modifiedAtEpochMillis, notLandedStraightAway, catchRecordReference, " +
                "lateSubmissionWarningAcknowledged, createdAtEpochMillis, submittedAtEpochMillis, " +
                "syncedAtEpochMillis) VALUES ('draft-1', 'vessel-1', 1, 1, 1, 2026, NULL, " +
                "NULL, NULL, NULL, NULL, NULL, NULL, 'Draft', 1000, NULL, NULL, 0, 1000, NULL, NULL)",
        )
        db.close()
    }
}
