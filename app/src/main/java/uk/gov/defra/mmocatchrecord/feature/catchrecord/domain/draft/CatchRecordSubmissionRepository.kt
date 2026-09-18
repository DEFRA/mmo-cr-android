package uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft

/**
 * Submits a completed [CatchRecordDraft] to the fishing-authority backend. Stage-1/Phase-8 has only a
 * bundled local stub implementation (no real backend exists yet) — see
 * `uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.StubCatchRecordSubmissionRepository`
 * and ADR 0009. Used by both the online "Accept and submit trip details" path
 * (`CatchRecordFlowViewModel`) and the offline background retry path (`CatchRecordSyncWorker`), so both
 * paths exercise the exact same submission call.
 */
interface CatchRecordSubmissionRepository {
    suspend fun submit(draft: CatchRecordDraft): Result<Unit>
}
