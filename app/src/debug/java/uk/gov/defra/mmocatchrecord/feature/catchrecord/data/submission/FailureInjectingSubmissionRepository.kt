package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission

import uk.gov.defra.mmocatchrecord.debug.DebugSubmissionFailureToggle
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordSubmissionRepository

/** Debug-only decorator (CRAR-152 Phase C C5) — forces failure while [toggle] is on, else delegates. */
class FailureInjectingSubmissionRepository(
    private val delegate: CatchRecordSubmissionRepository,
    private val toggle: DebugSubmissionFailureToggle,
) : CatchRecordSubmissionRepository {
    override suspend fun submit(draft: CatchRecordDraft): Result<Unit> =
        if (toggle.enabled.value) {
            Result.failure(IllegalStateException("Debug-forced submission failure"))
        } else {
            delegate.submit(draft)
        }
}
