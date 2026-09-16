package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow

import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordSubmissionRepository

/** Test double for [CatchRecordSubmissionRepository] — succeeds or fails deterministically per test. */
class FakeCatchRecordSubmissionRepository(
    private val shouldSucceed: Boolean = true,
) : CatchRecordSubmissionRepository {
    var submittedDrafts: List<CatchRecordDraft> = emptyList()
        private set

    override suspend fun submit(draft: CatchRecordDraft): Result<Unit> {
        submittedDrafts = submittedDrafts + draft
        return if (shouldSucceed) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Simulated submission failure"))
        }
    }
}
