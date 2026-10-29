package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

// Bounds the one-second Trip ID bump; a real collision needs only one or two steps.
private const val MAX_REFERENCE_ATTEMPTS = 3600

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

    /** Flat list projection, not the full aggregate — excludes `Discarded` tombstones; see ADR 0014. */
    @Query(
        "SELECT id, vesselId, catchRecordReference, status, returnDay, returnMonth, returnYear, " +
            "createdAtEpochMillis, modifiedAtEpochMillis, submittedAtEpochMillis, syncedAtEpochMillis " +
            "FROM catch_record_draft WHERE status != 'Discarded' " +
            "ORDER BY modifiedAtEpochMillis DESC, id DESC",
    )
    fun observeRecordSummaries(): Flow<List<DraftSummaryRow>>

    @Query(
        "SELECT departurePortId, returnPortId FROM catch_record_draft " +
            "WHERE vesselId = :vesselId AND status IN ('PendingSync', 'Submitted') " +
            "ORDER BY modifiedAtEpochMillis DESC, id DESC",
    )
    suspend fun findSubmittedTripPorts(vesselId: String): List<TripPortsRow>

    /** App-start reconciliation sweep target rows (CRAR-152 Phase C FR7) — see [CatchRecordSyncSweep]. */
    @Query("SELECT id FROM catch_record_draft WHERE status = 'PendingSync'")
    suspend fun findPendingSyncDraftIds(): List<String>

    @Transaction
    @Query("SELECT * FROM catch_record_draft WHERE id = :draftId")
    suspend fun getDraftWithChildren(draftId: String): DraftWithChildren?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDraft(entity: DraftEntity)

    /** Inserts [entity] only if it doesn't conflict with the v10 unique Trip ID index; IGNORE not
     *  REPLACE, since REPLACE would cascade-delete another draft's rows — see [createDraft]. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDraftIfAbsent(entity: DraftEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGearUses(entities: List<GearUseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeasurements(entities: List<MeasurementEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpeciesWeights(entities: List<SpeciesWeightEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLandingStorage(entities: List<LandingStorageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotLandedSpecies(entities: List<NotLandedSpeciesEntity>)

    @Query("DELETE FROM catch_record_gear_use WHERE draftId = :draftId")
    suspend fun deleteGearUsesForDraft(draftId: String)

    @Query("DELETE FROM catch_record_landing_storage WHERE draftId = :draftId")
    suspend fun deleteLandingStorageForDraft(draftId: String)

    @Query("DELETE FROM catch_record_not_landed_species WHERE draftId = :draftId")
    suspend fun deleteNotLandedSpeciesForDraft(draftId: String)

    @Query("DELETE FROM catch_record_draft WHERE id = :draftId")
    suspend fun deleteDraftById(draftId: String)

    /**
     * Upserts [draft] then wholesale-replaces its gear-use/landing-storage/not-landed-species children
     * (and, via cascade, the measurement/species-weight grandchildren) in a single atomic transaction.
     */
    @Transaction
    suspend fun replaceDraftAggregate(
        draft: DraftEntity,
        gearUses: List<GearUseEntity>,
        measurements: List<MeasurementEntity>,
        speciesWeights: List<SpeciesWeightEntity>,
        landingStorage: List<LandingStorageEntity>,
        notLandedSpecies: List<NotLandedSpeciesEntity>,
    ) {
        // REPLACE would delete (and cascade) a different draft holding the same Trip ID.
        draft.catchRecordReference?.let { reference ->
            check(!referenceUsedByOtherDraft(reference, draft.id)) { "Catch record reference already in use" }
        }
        upsertDraft(draft)
        deleteGearUsesForDraft(draft.id)
        deleteLandingStorageForDraft(draft.id)
        deleteNotLandedSpeciesForDraft(draft.id)
        if (gearUses.isNotEmpty()) insertGearUses(gearUses)
        if (measurements.isNotEmpty()) insertMeasurements(measurements)
        if (speciesWeights.isNotEmpty()) insertSpeciesWeights(speciesWeights)
        if (landingStorage.isNotEmpty()) insertLandingStorage(landingStorage)
        if (notLandedSpecies.isNotEmpty()) insertNotLandedSpecies(notLandedSpecies)
    }

    @Query("SELECT EXISTS(SELECT 1 FROM catch_record_draft WHERE catchRecordReference = :reference)")
    suspend fun referenceExists(reference: String): Boolean

    @Query(
        "SELECT EXISTS(SELECT 1 FROM catch_record_draft WHERE catchRecordReference = :reference AND id != :draftId)",
    )
    suspend fun referenceUsedByOtherDraft(
        reference: String,
        draftId: String,
    ): Boolean

    /** Creates [candidate] as a new, independent draft (ADR 0018); a Trip ID in use is advanced one second
     *  at a time until free, and a duplicate id or unexpected conflict fails fast. */
    @Transaction
    suspend fun createDraft(candidate: DraftEntity): DraftEntity {
        check(findDraftEntity(candidate.id) == null) { "Draft '${candidate.id}' already exists" }
        var reference = candidate.catchRecordReference
        var attempts = 0
        while (reference != null && referenceExists(reference)) {
            check(++attempts <= MAX_REFERENCE_ATTEMPTS) { "No free catch record reference found" }
            reference = CatchRecordReferenceGenerator.nextAfter(reference)
        }
        val unique = candidate.copy(catchRecordReference = reference)
        check(insertDraftIfAbsent(unique) != -1L) { "Draft '${candidate.id}' conflicted unexpectedly on insert" }
        return unique
    }
}
