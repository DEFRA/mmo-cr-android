package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * SQLCipher-encrypted (see ADR 0006) Room database for the catch-record draft aggregate. Opened via
 * `net.sqlcipher.database.SupportFactory` as its `openHelperFactory` — see `di/DatabaseModule.kt`.
 */
@Database(
    entities = [
        DraftEntity::class,
        GearUseEntity::class,
        MeasurementEntity::class,
        SpeciesWeightEntity::class,
        LandingStorageEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class CatchRecordDatabase : RoomDatabase() {
    abstract fun catchRecordDraftDao(): CatchRecordDraftDao
}
