package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local

import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraftRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import javax.inject.Inject

/**
 * Room-backed, offline-first [CatchRecordDraftRepository] implementation. See ADR 0006 for the
 * SQLCipher-encrypted database this reads/writes, and the class doc comment on
 * [CatchRecordDraftRepository] for the "one active draft per vessel" (draft singularity) rule this
 * enforces.
 */
class RoomCatchRecordDraftRepository
    @Inject
    constructor(
        private val dao: CatchRecordDraftDao,
        private val idFactory: () -> String,
        private val clock: () -> Long,
    ) : CatchRecordDraftRepository {
        override suspend fun getActiveDraft(vesselId: String): Result<CatchRecordDraft?> =
            runCatching { dao.getActiveDraftWithChildren(vesselId)?.let(DraftMappers::toDomain) }

        override suspend fun getAnyActiveDraft(): Result<CatchRecordDraft?> =
            runCatching { dao.getMostRecentlyModifiedActiveDraftWithChildren()?.let(DraftMappers::toDomain) }

        override suspend fun getDraftById(draftId: String): Result<CatchRecordDraft?> =
            runCatching { dao.getDraftWithChildren(draftId)?.let(DraftMappers::toDomain) }

        override suspend fun startDraft(vesselId: String): Result<CatchRecordDraft> =
            runCatching {
                val creationTime = clock()
                val candidate =
                    DraftEntity(
                        id = idFactory(),
                        vesselId = vesselId,
                        isTripToday = null,
                        departureDay = null,
                        departureMonth = null,
                        departureYear = null,
                        returnDay = null,
                        returnMonth = null,
                        returnYear = null,
                        departurePortId = null,
                        departurePortSelectionMode = null,
                        returnPortId = null,
                        returnPortSelectionMode = null,
                        status = DraftStatus.Draft.name,
                        modifiedAtEpochMillis = creationTime,
                        catchRecordReference = CatchRecordReferenceGenerator.generate(creationTime),
                    )
                // Race-safe find-or-create (see ADR 0010): returns the vessel's existing active draft
                // unchanged if one already exists (including one created concurrently by another caller),
                // never a duplicate.
                val winningEntity = dao.findOrCreateActiveDraft(vesselId, candidate)
                dao.getDraftWithChildren(winningEntity.id)?.let(DraftMappers::toDomain)
                    ?: error("Draft '${winningEntity.id}' vanished immediately after find-or-create")
            }

        override suspend fun saveDraft(draft: CatchRecordDraft): Result<CatchRecordDraft> =
            runCatching {
                val updated = draft.copy(modifiedAtEpochMillis = clock())
                persist(updated)
                updated
            }

        override suspend fun deleteDraft(draftId: String): Result<Unit> = runCatching { dao.deleteDraftById(draftId) }

        override suspend fun markReadyToSubmit(draftId: String): Result<CatchRecordDraft> =
            runCatching {
                val existing =
                    dao.getDraftWithChildren(draftId)?.let(DraftMappers::toDomain)
                        ?: error("No draft found with id $draftId")
                val updated = existing.copy(status = DraftStatus.ReadyToSubmit, modifiedAtEpochMillis = clock())
                persist(updated)
                updated
            }

        private suspend fun persist(draft: CatchRecordDraft) {
            val entities = DraftMappers.toEntities(draft)
            dao.replaceDraftAggregate(
                draft = entities.draft,
                gearUses = entities.gearUses,
                measurements = entities.measurements,
                speciesWeights = entities.speciesWeights,
                landingStorage = entities.landingStorage,
                notLandedSpecies = entities.notLandedSpecies,
            )
        }
    }
