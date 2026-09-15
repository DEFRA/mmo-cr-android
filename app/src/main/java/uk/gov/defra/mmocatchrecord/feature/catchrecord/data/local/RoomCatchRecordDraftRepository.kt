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

        override suspend fun startDraft(vesselId: String): Result<CatchRecordDraft> =
            runCatching {
                val existing = dao.getActiveDraftWithChildren(vesselId)
                if (existing != null) {
                    return@runCatching DraftMappers.toDomain(existing)
                }

                val newDraft =
                    CatchRecordDraft(
                        id = idFactory(),
                        vesselId = vesselId,
                        status = DraftStatus.Draft,
                        modifiedAtEpochMillis = clock(),
                    )
                persist(newDraft)
                newDraft
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
            )
        }
    }
