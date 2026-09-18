package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Real Room [Migration]s for every [CatchRecordDatabase] schema version bump, v1 through v6 — see ADR 0010.
 * Each migration's SQL is derived directly from the committed `app/schemas` JSON history (the authoritative
 * record of each version's actual column/table shape), never destructively dropping/recreating the whole
 * database. Registered via `.addMigrations(...)` in `di/DatabaseModule.kt`.
 */
object CatchRecordMigrations {
    /** v1 -> v2 (Phase 3): [GearUseEntity] gained `numberOfShots`/`confirmedUsedOnTrip`. */
    val MIGRATION_1_2 =
        object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE catch_record_gear_use ADD COLUMN numberOfShots INTEGER")
                db.execSQL(
                    "ALTER TABLE catch_record_gear_use ADD COLUMN confirmedUsedOnTrip INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

    /** v2 -> v3 (Phase 4): [GearUseEntity]'s `statRectangleId` renamed to `statisticalSubRectangleCode`. */
    val MIGRATION_2_3 =
        object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE catch_record_gear_use RENAME COLUMN statRectangleId TO statisticalSubRectangleCode",
                )
            }
        }

    /**
     * v3 -> v4 (Phase 5): [DraftEntity] gained `notLandedStraightAway`; [SpeciesWeightEntity]'s
     * `retainedAboveMcrsKg`/`retainedBelowMcrsKg`/`discardedKg` (non-null) were replaced by
     * `weightAboveMinimumSizeKg`/`weightBelowMinimumSizeKg`/`weightLegallyDiscardedKg` (nullable) plus a new
     * `confirmedCaught` column; [NotLandedSpeciesEntity] is a new table. Every pre-existing species-weight
     * row is mapped with `confirmedCaught = 1`: under the pre-v4 model, any persisted row already
     * represented a captured catch entry (there was no "added but unconfirmed" state yet), so this is a
     * safe, data-preserving best-effort default rather than an invented value.
     */
    val MIGRATION_3_4 =
        object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE catch_record_draft ADD COLUMN notLandedStraightAway INTEGER")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS catch_record_species_weight_new (" +
                        "`id` TEXT NOT NULL, `gearUseId` TEXT NOT NULL, `speciesId` TEXT NOT NULL, " +
                        "`weightAboveMinimumSizeKg` REAL, `weightBelowMinimumSizeKg` REAL, " +
                        "`weightLegallyDiscardedKg` REAL, `confirmedCaught` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`), FOREIGN KEY(`gearUseId`) REFERENCES `catch_record_gear_use`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL(
                    "INSERT INTO catch_record_species_weight_new " +
                        "(id, gearUseId, speciesId, weightAboveMinimumSizeKg, weightBelowMinimumSizeKg, " +
                        "weightLegallyDiscardedKg, confirmedCaught) " +
                        "SELECT id, gearUseId, speciesId, retainedAboveMcrsKg, retainedBelowMcrsKg, discardedKg, 1 " +
                        "FROM catch_record_species_weight",
                )
                db.execSQL("DROP TABLE catch_record_species_weight")
                db.execSQL("ALTER TABLE catch_record_species_weight_new RENAME TO catch_record_species_weight")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_catch_record_species_weight_gearUseId " +
                        "ON catch_record_species_weight (gearUseId)",
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS catch_record_not_landed_species (" +
                        "`rowId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `draftId` TEXT NOT NULL, " +
                        "`speciesId` TEXT NOT NULL, `weightAboveMinimumSizeKeptOnboardKg` REAL, " +
                        "FOREIGN KEY(`draftId`) REFERENCES `catch_record_draft`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_catch_record_not_landed_species_draftId " +
                        "ON catch_record_not_landed_species (draftId)",
                )
            }
        }

    /**
     * v4 -> v5 (Phase 8): [DraftEntity] gained `catchRecordReference` and
     * `lateSubmissionWarningAcknowledged` (defaulting existing rows to `false`/`0` — a pre-existing draft
     * has not seen a late-submission warning it didn't exist to be shown).
     */
    val MIGRATION_4_5 =
        object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE catch_record_draft ADD COLUMN catchRecordReference TEXT")
                db.execSQL(
                    "ALTER TABLE catch_record_draft ADD COLUMN lateSubmissionWarningAcknowledged " +
                        "INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

    /**
     * v5 -> v6 (this remediation, see ADR 0010): enforces "one active draft per vessel" with a genuine
     * partial unique index, first deduplicating any pre-existing violation (keeping the most-recently
     * modified active draft per vessel, demoting the rest to [uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus.Discarded]
     * — never deleting rows outright) so the index creation itself cannot fail against already-corrupted
     * local state.
     */
    val MIGRATION_5_6 =
        object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                dedupeActiveDraftsPerVessel(db)
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_catch_record_draft_active_vessel " +
                        "ON catch_record_draft (vesselId) WHERE status IN ('Draft', 'ReadyToSubmit')",
                )
            }

            private fun dedupeActiveDraftsPerVessel(db: SupportSQLiteDatabase) {
                val activeDraftsByVessel = mutableMapOf<String, MutableList<Pair<String, Long>>>()
                db
                    .query(
                        "SELECT id, vesselId, modifiedAtEpochMillis FROM catch_record_draft " +
                            "WHERE status IN ('Draft', 'ReadyToSubmit')",
                    ).use { cursor ->
                        val idIndex = cursor.getColumnIndexOrThrow("id")
                        val vesselIdIndex = cursor.getColumnIndexOrThrow("vesselId")
                        val modifiedAtIndex = cursor.getColumnIndexOrThrow("modifiedAtEpochMillis")
                        while (cursor.moveToNext()) {
                            val id = cursor.getString(idIndex)
                            val vesselId = cursor.getString(vesselIdIndex)
                            val modifiedAt = cursor.getLong(modifiedAtIndex)
                            activeDraftsByVessel.getOrPut(vesselId) { mutableListOf() } += id to modifiedAt
                        }
                    }

                activeDraftsByVessel.values
                    .filter { it.size > 1 }
                    .forEach { duplicates ->
                        val keepId = duplicates.maxWithOrNull(compareBy({ it.second }, { it.first }))?.first
                        duplicates.forEach { (id, _) ->
                            if (id != keepId) {
                                db.execSQL(
                                    "UPDATE catch_record_draft SET status = 'Discarded' WHERE id = ?",
                                    arrayOf(id),
                                )
                            }
                        }
                    }
            }
        }

    val ALL =
        arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
}
