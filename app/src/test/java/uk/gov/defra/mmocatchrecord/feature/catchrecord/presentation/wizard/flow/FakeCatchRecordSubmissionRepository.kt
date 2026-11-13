package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow

import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordSubmissionRepository

/** Test double for [CatchRecordSubmissionRepository] — succeeds or fails deterministically per test. */
class FakeCatchRecordSubmissionRepository(
    private val shouldSucceed: Boolean = true,
) : CatchRecordSubmissionRepository {
    var submittedDrafts: List<CatchRecordDraft> = emptyList()
        private set

    /** Throws [throwOnNextSubmit] from the next [submit] call instead of returning, then clears itself. */
    var throwOnNextSubmit: Throwable? = null

    override suspend fun submit(draft: CatchRecordDraft): Result<Unit> {
        throwOnNextSubmit?.let {
            throwOnNextSubmit = null
            throw it
        }
        submittedDrafts = submittedDrafts + draft
        return if (shouldSucceed) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Simulated submission failure"))
        }
    }
}
