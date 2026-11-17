package uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft

/**
 * Submits a completed [CatchRecordDraft] to the fishing-authority backend; a debug-only stub also exists —
 * see `uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.StubCatchRecordSubmissionRepository`
 * and ADR 0009. Used by both the online "Accept and submit trip details" path
 * (`CatchRecordFlowViewModel`) and the offline background retry path (`CatchRecordSyncWorker`), so both
 * paths exercise the exact same submission call.
 */
fun interface CatchRecordSubmissionRepository {
    /** Returns the latest persisted draft — callers must never overwrite it with a stale snapshot. */
    suspend fun submit(draft: CatchRecordDraft): Result<CatchRecordDraft>
}
