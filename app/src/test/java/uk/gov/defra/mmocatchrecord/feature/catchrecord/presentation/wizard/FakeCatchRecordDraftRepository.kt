@file:Suppress("detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard

import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraftRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus

class FakeCatchRecordDraftRepository(
    private val idFactory: () -> String = { "draft-id" },
    private val clock: () -> Long = { 0L },
) : CatchRecordDraftRepository {
    private val drafts = mutableMapOf<String, CatchRecordDraft>()
    val deletedDraftIds = mutableListOf<String>()
    var failNextOperation: Boolean = false

    override suspend fun getActiveDraft(vesselId: String): Result<CatchRecordDraft?> {
        if (failNextOperation) return failure()
        return Result.success(drafts.values.firstOrNull { it.vesselId == vesselId && it.status.isActive })
    }

    override suspend fun getAnyActiveDraft(): Result<CatchRecordDraft?> {
        if (failNextOperation) return failure()
        return Result.success(drafts.values.filter { it.status.isActive }.maxByOrNull { it.modifiedAtEpochMillis })
    }

    override suspend fun startDraft(vesselId: String): Result<CatchRecordDraft> {
        if (failNextOperation) return failure()
        val existing = drafts.values.firstOrNull { it.vesselId == vesselId && it.status.isActive }
        if (existing != null) return Result.success(existing)
        val draft =
            CatchRecordDraft(
                id = idFactory(),
                vesselId = vesselId,
                status = DraftStatus.Draft,
                modifiedAtEpochMillis = clock(),
            )
        drafts[draft.id] = draft
        return Result.success(draft)
    }

    override suspend fun saveDraft(draft: CatchRecordDraft): Result<CatchRecordDraft> {
        if (failNextOperation) return failure()
        drafts[draft.id] = draft
        return Result.success(draft)
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
        return Result.failure(IllegalStateException("Simulated failure"))
    }
}
