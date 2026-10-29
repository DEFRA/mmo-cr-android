package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraftRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import uk.gov.defra.mmocatchrecord.feature.home.domain.CatchRecordSummary
import javax.inject.Inject

/**
 * Room-backed, offline-first [CatchRecordDraftRepository] implementation. See ADR 0006 for the
 * SQLCipher-encrypted database this reads/writes, and ADR 0018 for why every draft is independent.
 */
class RoomCatchRecordDraftRepository
    @Inject
    constructor(
        private val dao: CatchRecordDraftDao,
        private val clock: () -> Long,
    ) : CatchRecordDraftRepository {
        override suspend fun getDraftById(draftId: String): Result<CatchRecordDraft?> =
            runCatching { dao.getDraftWithChildren(draftId)?.let(DraftMappers::toDomain) }

        override suspend fun startDraft(candidate: CatchRecordDraft): Result<CatchRecordDraft> =
            runCatching {
                val creationTime = clock()
                val stamped =
                    candidate.copy(
                        createdAtEpochMillis = candidate.createdAtEpochMillis ?: creationTime,
                        modifiedAtEpochMillis = creationTime,
                    )
                val created = dao.createDraft(DraftMappers.toEntities(stamped).draft)
                dao.getDraftWithChildren(created.id)?.let(DraftMappers::toDomain)
                    ?: error("Draft '${created.id}' vanished immediately after creation")
            }

        override suspend fun saveDraft(draft: CatchRecordDraft): Result<CatchRecordDraft> =
            runCatching {
                val now = clock()
                val isSynced = draft.status == DraftStatus.Submitted
                val isSubmissionAccepted = isSynced || draft.status == DraftStatus.PendingSync
                val updated =
                    draft.copy(
                        modifiedAtEpochMillis = now,
                        submittedAtEpochMillis = draft.submittedAtEpochMillis ?: now.takeIf { isSubmissionAccepted },
                        syncedAtEpochMillis = draft.syncedAtEpochMillis ?: now.takeIf { isSynced },
                    )
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

        override fun observeRecordSummaries(): Flow<List<CatchRecordSummary>> =
            dao.observeRecordSummaries().map { rows -> rows.mapNotNull(DraftSummaryMappers::toDomain) }

        override suspend fun getPreviouslyUsedPortIds(vesselId: String): Result<List<String>> =
            runCatching {
                dao
                    .findSubmittedTripPorts(vesselId)
                    .flatMap { listOfNotNull(it.departurePortId, it.returnPortId) }
                    .distinct()
            }
    }
