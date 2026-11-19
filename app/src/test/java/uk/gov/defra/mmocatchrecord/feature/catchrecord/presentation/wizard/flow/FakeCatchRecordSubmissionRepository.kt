package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow

import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordSubmissionRepository

/** Test double for [CatchRecordSubmissionRepository] — succeeds or fails deterministically per test. */
class FakeCatchRecordSubmissionRepository(
    private val shouldSucceed: Boolean = true,
    private val failure: Throwable = IllegalStateException("Simulated submission failure"),
    /** Simulates partial progress already persisted server-side before a failure (B1 test support). */
    private val onSubmit: suspend (CatchRecordDraft) -> CatchRecordDraft = { it },
) : CatchRecordSubmissionRepository {
    var submittedDrafts: List<CatchRecordDraft> = emptyList()
        private set

    /** Throws [throwOnNextSubmit] from the next [submit] call instead of returning, then clears itself. */
    var throwOnNextSubmit: Throwable? = null

    override suspend fun submit(draft: CatchRecordDraft): Result<CatchRecordDraft> {
        throwOnNextSubmit?.let {
            throwOnNextSubmit = null
            throw it
        }
        submittedDrafts = submittedDrafts + draft
        val progressed = onSubmit(draft)
        return if (shouldSucceed) {
            Result.success(progressed)
        } else {
            Result.failure(failure)
        }
    }
}
