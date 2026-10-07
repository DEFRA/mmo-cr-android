@file:Suppress("detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow

import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraftFactory
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraftRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus

class FakeCatchRecordDraftRepository(
    private val idFactory: () -> String = { "draft-id" },
    private val clock: () -> Long = { 0L },
) : CatchRecordDraftRepository {
    private val drafts = mutableMapOf<String, CatchRecordDraft>()
    val deletedDraftIds = mutableListOf<String>()
    var failNextOperation: Boolean = false

    /** Number of real [startDraft] invocations — used by BR-XX tests to assert "exactly one write". */
    var startDraftCallCount: Int = 0
        private set

    override suspend fun getActiveDraft(vesselId: String): Result<CatchRecordDraft?> {
        if (failNextOperation) return failure()
        return Result.success(drafts.values.firstOrNull { it.vesselId == vesselId && it.status.isActive })
    }

    override suspend fun getAnyActiveDraft(): Result<CatchRecordDraft?> {
        if (failNextOperation) return failure()
        return Result.success(drafts.values.filter { it.status.isActive }.maxByOrNull { it.modifiedAtEpochMillis })
    }

    override suspend fun getDraftById(draftId: String): Result<CatchRecordDraft?> {
        if (failNextOperation) return failure()
        return Result.success(drafts[draftId])
    }

    override suspend fun startDraft(candidate: CatchRecordDraft): Result<CatchRecordDraft> {
        startDraftCallCount++
        if (failNextOperation) return failure()
        val existing = drafts.values.firstOrNull { it.vesselId == candidate.vesselId && it.status.isActive }
        if (existing != null) return Result.success(existing)
        val creationTime = clock()
        val stamped =
            candidate.copy(
                createdAtEpochMillis = candidate.createdAtEpochMillis ?: creationTime,
                modifiedAtEpochMillis = creationTime,
            )
        drafts[stamped.id] = stamped
        return Result.success(stamped)
    }

    /**
     * Test-only convenience mirroring the pre-BR-XX single-argument API: builds a candidate via
     * [CatchRecordDraftFactory] so existing seed-only call sites keep compiling unchanged.
     */
    suspend fun startDraft(vesselId: String): Result<CatchRecordDraft> {
        val id = idFactory()
        return startDraft(CatchRecordDraftFactory.newDraft(vesselId = vesselId, id = id, reference = "ref-$id"))
    }

    /** Mirrors [uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local.RoomCatchRecordDraftRepository]. */
    override suspend fun saveDraft(draft: CatchRecordDraft): Result<CatchRecordDraft> {
        if (failNextOperation) return failure()
        val now = clock()
        val isSynced = draft.status == DraftStatus.Submitted
        val isSubmissionAccepted = isSynced || draft.status == DraftStatus.PendingSync
        val updated =
            draft.copy(
                submittedAtEpochMillis = draft.submittedAtEpochMillis ?: now.takeIf { isSubmissionAccepted },
                syncedAtEpochMillis = draft.syncedAtEpochMillis ?: now.takeIf { isSynced },
            )
        drafts[updated.id] = updated
        return Result.success(updated)
    }

    override suspend fun deleteDraft(draftId: String): Result<Unit> {
        if (failNextOperation) return failure()
        drafts.remove(draftId)
        deletedDraftIds += draftId
        return Result.success(Unit)
    }

    override suspend fun markReadyToSubmit(draftId: String): Result<CatchRecordDraft> {
        if (failNextOperation) return failure()
        val existing = drafts[draftId] ?: return Result.failure(NoSuchElementException("No draft $draftId"))
        val updated = existing.copy(status = DraftStatus.ReadyToSubmit)
        drafts[draftId] = updated
        return Result.success(updated)
    }

    private fun <T> failure(): Result<T> {
        failNextOperation = false
        // A generic repository I/O failure — matches SafeErrorMapper's transient/retryable classification
        // for IOException (see that class's docs), which is what a real Room/disk failure looks like.
        return Result.failure(java.io.IOException("Simulated failure"))
    }
}
