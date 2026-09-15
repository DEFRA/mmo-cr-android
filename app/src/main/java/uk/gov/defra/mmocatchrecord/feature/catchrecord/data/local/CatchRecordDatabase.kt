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
 * for the Phase 5B trip-level follow-up. As with the v1 -> v2 and v2 -> v3 bumps, no real
 * [androidx.room.migration.Migration] is written; see `di/DatabaseModule.kt` for why.
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
    version = 4,
    exportSchema = true,
)
abstract class CatchRecordDatabase : RoomDatabase() {
    abstract fun catchRecordDraftDao(): CatchRecordDraftDao
}
