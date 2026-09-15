package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

private const val ACTIVE_STATUSES_CLAUSE = "status IN ('Draft', 'ReadyToSubmit')"

/**
 * DAO for the catch-record draft aggregate. [replaceDraftAggregate] is the single write path used for
 * every "save and continue" autosave: it upserts the root row and wholesale-replaces the child rows,
 * which is simple and correct for Phase 0's low write frequency (a delta/diff-based update is not needed).
 */
@Suppress("TooManyFunctions") // Cohesive aggregate DAO: root + 4 child tables, each needing read/write/delete.
@Dao
interface CatchRecordDraftDao {
    @Query("SELECT * FROM catch_record_draft WHERE id = :draftId")
    suspend fun findDraftEntity(draftId: String): DraftEntity?

    @Query("SELECT * FROM catch_record_draft WHERE vesselId = :vesselId AND $ACTIVE_STATUSES_CLAUSE LIMIT 1")
    suspend fun findActiveDraftEntity(vesselId: String): DraftEntity?

    @Transaction
    @Query("SELECT * FROM catch_record_draft WHERE id = :draftId")
    suspend fun getDraftWithChildren(draftId: String): DraftWithChildren?

    @Transaction
    @Query("SELECT * FROM catch_record_draft WHERE vesselId = :vesselId AND $ACTIVE_STATUSES_CLAUSE LIMIT 1")
    suspend fun getActiveDraftWithChildren(vesselId: String): DraftWithChildren?

    @Transaction
    @Query(
        "SELECT * FROM catch_record_draft WHERE $ACTIVE_STATUSES_CLAUSE ORDER BY modifiedAtEpochMillis DESC LIMIT 1",
    )
    suspend fun getMostRecentlyModifiedActiveDraftWithChildren(): DraftWithChildren?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDraft(entity: DraftEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGearUses(entities: List<GearUseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeasurements(entities: List<MeasurementEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpeciesWeights(entities: List<SpeciesWeightEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLandingStorage(entities: List<LandingStorageEntity>)

    @Query("DELETE FROM catch_record_gear_use WHERE draftId = :draftId")
    suspend fun deleteGearUsesForDraft(draftId: String)

    @Query("DELETE FROM catch_record_landing_storage WHERE draftId = :draftId")
    suspend fun deleteLandingStorageForDraft(draftId: String)

    @Query("DELETE FROM catch_record_draft WHERE id = :draftId")
    suspend fun deleteDraftById(draftId: String)

    /**
     * Upserts [draft] then wholesale-replaces its gear-use/landing-storage children (and, via cascade,
     * the measurement/species-weight grandchildren) in a single atomic transaction.
     */
    @Transaction
    suspend fun replaceDraftAggregate(
        draft: DraftEntity,
        gearUses: List<GearUseEntity>,
        measurements: List<MeasurementEntity>,
        speciesWeights: List<SpeciesWeightEntity>,
        landingStorage: List<LandingStorageEntity>,
    ) {
        upsertDraft(draft)
        deleteGearUsesForDraft(draft.id)
        deleteLandingStorageForDraft(draft.id)
        if (gearUses.isNotEmpty()) insertGearUses(gearUses)
        if (measurements.isNotEmpty()) insertMeasurements(measurements)
        if (speciesWeights.isNotEmpty()) insertSpeciesWeights(speciesWeights)
        if (landingStorage.isNotEmpty()) insertLandingStorage(landingStorage)
    }
}
