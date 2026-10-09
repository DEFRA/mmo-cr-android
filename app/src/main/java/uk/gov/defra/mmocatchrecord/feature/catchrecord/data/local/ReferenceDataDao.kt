package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * DAO for the cached reference-data tables (ADR 0015). Every `replace*` is a transactional delete-then-
 * insert; suspend functions already run off the main thread, so the 624-row `ports` insert needs no more.
 */
@Suppress("TooManyFunctions") // Cohesive cache DAO: 4 datasets + metadata, each needing read/replace.
@Dao
interface ReferenceDataDao {
    @Query("DELETE FROM reference_data_vessel")
    suspend fun deleteAllVessels()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVessels(entities: List<ReferenceDataVesselEntity>)

    @Transaction
    suspend fun replaceVessels(entities: List<ReferenceDataVesselEntity>) {
        deleteAllVessels()
        insertVessels(entities)
    }

    @Query("SELECT * FROM reference_data_vessel")
    suspend fun getVessels(): List<ReferenceDataVesselEntity>

    @Query("DELETE FROM reference_data_port")
    suspend fun deleteAllPorts()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPorts(entities: List<ReferenceDataPortEntity>)

    @Transaction
    suspend fun replacePorts(entities: List<ReferenceDataPortEntity>) {
        deleteAllPorts()
        insertPorts(entities)
    }

    @Query("SELECT * FROM reference_data_port")
    suspend fun getPorts(): List<ReferenceDataPortEntity>

    @Query("DELETE FROM reference_data_species")
    suspend fun deleteAllSpecies()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpecies(entities: List<ReferenceDataSpeciesEntity>)

    @Transaction
    suspend fun replaceSpecies(entities: List<ReferenceDataSpeciesEntity>) {
        deleteAllSpecies()
        insertSpecies(entities)
    }

    @Query("SELECT * FROM reference_data_species")
    suspend fun getSpecies(): List<ReferenceDataSpeciesEntity>

    @Query("DELETE FROM reference_data_gear WHERE vesselLengthBand = :band")
    suspend fun deleteGearsForBand(band: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGears(entities: List<ReferenceDataGearEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGearMeasurements(entities: List<ReferenceDataGearMeasurementEntity>)

    /** Band-scoped replace: deleting [deleteGearsForBand]'s rows cascades to their measurement rows. */
    @Transaction
    suspend fun replaceGearsForBand(
        band: String,
        gears: List<ReferenceDataGearEntity>,
        measurements: List<ReferenceDataGearMeasurementEntity>,
    ) {
        deleteGearsForBand(band)
        insertGears(gears)
        insertGearMeasurements(measurements)
    }

    @Query("SELECT * FROM reference_data_gear WHERE vesselLengthBand = :band ORDER BY orderIndex")
    suspend fun getGearsForBand(band: String): List<ReferenceDataGearEntity>

    @Query(
        "SELECT * FROM reference_data_gear_measurement WHERE vesselLengthBand = :band ORDER BY gearId, orderIndex",
    )
    suspend fun getGearMeasurementsForBand(band: String): List<ReferenceDataGearMeasurementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMetadata(entity: ReferenceDataMetadataEntity)

    @Query("SELECT * FROM reference_data_metadata WHERE datasetKey = :datasetKey")
    suspend fun getMetadata(datasetKey: String): ReferenceDataMetadataEntity?
}
