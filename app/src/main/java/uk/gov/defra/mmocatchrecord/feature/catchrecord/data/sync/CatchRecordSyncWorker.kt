package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import uk.gov.defra.mmocatchrecord.core.error.CatchRecordErrorClassification
import uk.gov.defra.mmocatchrecord.core.error.SafeErrorMapper
import uk.gov.defra.mmocatchrecord.core.error.submissionFailureReasonFor
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraftRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraftValidation
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordSubmissionRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftSubmissionValidation
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.SubmissionFailureReason

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
            val draftId = inputData.getString(KEY_DRAFT_ID)
            val draft = draftId?.let { draftRepository.getDraftById(it).getOrNull() } ?: return Result.failure()

            // Already handled (e.g. a previous run of this same work succeeded before a process death, or
            // the draft was discarded) — nothing left to do, and re-submitting would not be idempotent.
            if (draft.status != DraftStatus.PendingSync) return Result.success()

            // Defence-in-depth (finding: aggregate validation "even via corrupted/persisted... events"):
            // re-validate completeness before every retry attempt, not just once at the original
            // acceptDeclarationAndSubmit() call. An invalid draft is a terminal, non-retryable condition —
            // retrying cannot fix bad data, so this fails rather than requesting Result.retry().
            if (CatchRecordDraftValidation.validateForSubmission(draft) is DraftSubmissionValidation.Invalid) {
                Timber.e("PendingSync draft %s failed submission validation; not retrying", draftId)
                return Result.failure()
            }

            return submissionRepository.submit(draft).fold(
                onSuccess = { markSubmitted(it) },
                onFailure = { handleFailure(draft, it) },
            )
        }

        private suspend fun markSubmitted(draft: CatchRecordDraft): Result =
            draftRepository.saveDraft(draft.copy(status = DraftStatus.Submitted)).fold(
                onSuccess = { Result.success() },
                onFailure = { Result.retry() },
            )

        /** 401 gets one same-attempt retry before giving up (ADR 0026); [draft] is reloaded first
         *  because the repository may have persisted progress even when the result is a failure. */
        private suspend fun handleFailure(
            draft: CatchRecordDraft,
            error: Throwable,
            isTokenRetry: Boolean = false,
        ): Result {
            val classification = SafeErrorMapper.classify(error)
            val latest = latestOrFallback(draft)
            if (classification == CatchRecordErrorClassification.NeedsAuthentication && !isTokenRetry) {
                return submissionRepository.submit(latest).fold(
                    onSuccess = { markSubmitted(it) },
                    onFailure = { handleFailure(latest, it, isTokenRetry = true) },
                )
            }
            if (classification == CatchRecordErrorClassification.Transient && runAttemptCount < MAX_ATTEMPTS) {
                return Result.retry()
            }
            val reason =
                if (classification == CatchRecordErrorClassification.Transient) {
                    // S7: the retry cap, not the server, stopped this — a distinct reason from a genuine
                    // server rejection so the failed screen's copy doesn't blame the data.
                    SubmissionFailureReason.RetryLimitReached
                } else {
                    submissionFailureReasonFor(error)
                }
            return failTerminally(latest, reason)
        }

        /** The persisted row, falling back to [draft] only if the reload itself fails (B1). */
        private suspend fun latestOrFallback(draft: CatchRecordDraft): CatchRecordDraft =
            draftRepository.getDraftById(draft.id).getOrNull() ?: draft

        /** S6: if persisting the terminal state itself fails, retry the whole work rather than losing the
         *  failure reason to a dropped write — `Result.failure()` here would never be revisited. */
        private suspend fun failTerminally(
            draft: CatchRecordDraft,
            reason: SubmissionFailureReason,
        ): Result {
            Timber.e("Draft %s submission terminal (%s); not retrying", draft.id, reason)
            return draftRepository
                .saveDraft(draft.copy(status = DraftStatus.SubmissionFailed, submissionFailureReason = reason.name))
                .fold(
                    onSuccess = { Result.failure() },
                    onFailure = { Result.retry() },
                )
        }

        companion object {
            const val KEY_DRAFT_ID = "draftId"

            /** ADR 0009 amendment: caps indefinite exponential retry of a never-resolving transient failure. */
            const val MAX_ATTEMPTS = 8
        }
    }
