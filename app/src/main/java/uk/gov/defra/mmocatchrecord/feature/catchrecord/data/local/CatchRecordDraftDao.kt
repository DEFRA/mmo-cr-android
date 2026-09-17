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

    /**
     * Inserts [entity] only if it does not conflict with an existing row — in particular, the v6 partial
     * unique index (`index_catch_record_draft_active_vessel`, see ADR 0010) that allows at most one
     * `Draft`/`ReadyToSubmit` row per `vesselId`. Deliberately [OnConflictStrategy.IGNORE], never `REPLACE`:
     * replacing would silently delete a concurrently-created active draft for the same vessel (and, via
     * cascade, its child rows) rather than preserving it — see [findOrCreateActiveDraft].
     *
     * @return the inserted row's rowId, or -1 if the insert was ignored due to a conflict.
     */
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

    /**
     * Race-safe "find the vessel's active draft, or create [candidate] as its new one" — see ADR 0010. The
     * whole find+insert-if-absent sequence runs inside one Room `@Transaction`, so SQLite's write-transaction
     * serialization makes the overall operation atomic across concurrent callers: whichever caller's
     * transaction commits first "wins" (its insert succeeds), and every other concurrent caller's own
     * transaction only begins evaluating [findActiveDraftEntity] after that commit, so it always observes the
     * winner's row and returns it — the partial unique index guarantees at most one winner even if this
     * transactional serialization guarantee were ever weakened, since [insertDraftIfAbsent] is ignore-on-conflict
     * rather than throwing or replacing.
     */
    @Transaction
    suspend fun findOrCreateActiveDraft(
        vesselId: String,
        candidate: DraftEntity,
    ): DraftEntity {
        findActiveDraftEntity(vesselId)?.let { return it }
        val insertedRowId = insertDraftIfAbsent(candidate)
        if (insertedRowId != -1L) {
            return candidate
        }
        // Lost the race to a concurrent create — the unique index means an active draft now exists for
        // this vessel; return it rather than duplicating or failing (finding: "unique constraint returns
        // existing").
        return findActiveDraftEntity(vesselId)
            ?: error("Draft insert for vessel '$vesselId' conflicted but no active draft was found")
    }
}
