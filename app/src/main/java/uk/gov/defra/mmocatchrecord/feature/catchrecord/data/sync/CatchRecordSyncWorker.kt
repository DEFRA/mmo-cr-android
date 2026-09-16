package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraftRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordSubmissionRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus

/**
 * Background retry of a queued (`PendingSync`) catch-record submission once connectivity returns — see
 * [WorkManagerCatchRecordSyncScheduler] (which enqueues this with a `NetworkType.CONNECTED` constraint) and
 * ADR 0009. Calls the exact same [CatchRecordSubmissionRepository.submit] method as the online path in
 * `CatchRecordFlowViewModel`, so both submission paths are guaranteed to behave identically.
 *
 * Relies entirely on WorkManager's own built-in retry/backoff policy (the default exponential backoff) on
 * [androidx.work.ListenableWorker.Result.retry] — no custom retry/backoff logic is implemented here.
 */
@HiltWorker
class CatchRecordSyncWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val draftRepository: CatchRecordDraftRepository,
        private val submissionRepository: CatchRecordSubmissionRepository,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val draftId = inputData.getString(KEY_DRAFT_ID) ?: return Result.failure()
            val draft = draftRepository.getDraftById(draftId).getOrNull() ?: return Result.failure()

            // Already handled (e.g. a previous run of this same work succeeded before a process death, or
            // the draft was discarded) — nothing left to do, and re-submitting would not be idempotent.
            if (draft.status != DraftStatus.PendingSync) return Result.success()

            return submissionRepository
                .submit(draft)
                .fold(
                    onSuccess = {
                        draftRepository
                            .saveDraft(draft.copy(status = DraftStatus.Submitted))
                            .fold(
                                onSuccess = { Result.success() },
                                onFailure = { Result.retry() },
                            )
                    },
                    onFailure = { Result.retry() },
                )
        }

        companion object {
            const val KEY_DRAFT_ID = "draftId"
        }
    }
