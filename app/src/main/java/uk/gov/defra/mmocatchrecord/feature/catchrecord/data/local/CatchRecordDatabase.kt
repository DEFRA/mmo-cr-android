package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * SQLCipher-encrypted (see ADR 0006) Room database for the catch-record draft aggregate. Opened via
 * `net.sqlcipher.database.SupportFactory` as its `openHelperFactory` — see `di/DatabaseModule.kt`.
 *
 * v2 -> v3 (Phase 4): [GearUseEntity]'s `statRectangleId` column was renamed to
 * `statisticalSubRectangleCode` to reflect that it holds a recorded statistical sub-rectangle **code**
 * (e.g. `"38E95"`), not a reference-data row id — see [GearUseEntity] and
 * `uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse.statisticalSubRectangleCode`.
 *
 * v3 -> v4 (Phase 5): [SpeciesWeightEntity]'s `retainedAboveMcrsKg`/`retainedBelowMcrsKg`/`discardedKg`
 * (non-nullable `Double`) columns were replaced by `weightAboveMinimumSizeKg`/`weightBelowMinimumSizeKg`/
 * `weightLegallyDiscardedKg` (all nullable `Double?`) plus a new `confirmedCaught` column, to match the
 * confirmed species-checklist screenshots' field vocabulary and progressive-disclosure behaviour; and a
 * new [NotLandedSpeciesEntity] table plus [DraftEntity]'s new `notLandedStraightAway` column were added
 * for the Phase 5B trip-level follow-up.
 *
 * v4 -> v5 (Phase 8): [DraftEntity] gained `catchRecordReference` (the user-facing reference generated
 * once at draft creation, see [CatchRecordReferenceGenerator]) and `lateSubmissionWarningAcknowledged`
 * (breaks what would otherwise be an infinite wizard-step loop on the late-submission warning screen — see
 * `presentation.wizard.WizardStep`), plus [DraftStatus.PendingSync] as a new stored status value.
 *
 * v5 -> v6 (see ADR 0010): a genuine partial unique index — `index_catch_record_draft_active_vessel` on
 * `(vesselId) WHERE status IN ('Draft', 'ReadyToSubmit')` — now enforces "one active draft per vessel" at
 * the SQLite level (Room's `@Index` annotation cannot express a `WHERE` clause, so it is created via raw SQL
 * in [CatchRecordMigrations.MIGRATION_5_6]); [CatchRecordDraftDao.findOrCreateActiveDraft] is the
 * corresponding race-safe find-or-create DAO path.
 *
 * Every version bump above has a real [androidx.room.migration.Migration] registered in
 * [CatchRecordMigrations] (see `di/DatabaseModule.kt`) — a draft in progress must survive every schema
 * change, so `fallbackToDestructiveMigration` must never be reintroduced for this database (ADR 0010).
 */
@Database(
    entities = [
        DraftEntity::class,
        GearUseEntity::class,
        MeasurementEntity::class,
        SpeciesWeightEntity::class,
        LandingStorageEntity::class,
        NotLandedSpeciesEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class CatchRecordDatabase : RoomDatabase() {
    abstract fun catchRecordDraftDao(): CatchRecordDraftDao
}
