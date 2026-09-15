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
 * `uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse.statisticalSubRectangleCode`. As
 * with the v1 -> v2 bump, no real [androidx.room.migration.Migration] is written; see `di/DatabaseModule.kt`
 * for why.
 */
@Database(
    entities = [
        DraftEntity::class,
        GearUseEntity::class,
        MeasurementEntity::class,
        SpeciesWeightEntity::class,
        LandingStorageEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class CatchRecordDatabase : RoomDatabase() {
    abstract fun catchRecordDraftDao(): CatchRecordDraftDao
}
