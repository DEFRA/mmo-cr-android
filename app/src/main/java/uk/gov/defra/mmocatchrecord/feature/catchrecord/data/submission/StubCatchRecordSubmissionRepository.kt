package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission

import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordSubmissionRepository

/**
 * Bundled, local stub [CatchRecordSubmissionRepository] — there is no real fishing-authority backend yet
 * (Phase 8/ADR 0009), mirroring the Stage-1/ADR 0008 approach for reference data. Always succeeds: the
 * online-vs-offline branch is decided *before* this is ever called (see
 * `CatchRecordFlowViewModel.submitCatchRecord`/`CatchRecordSyncWorker`), so this only needs to model "the
 * network call itself succeeded" — it does not simulate transient network failures, which the offline path
 * (queue + WorkManager retry) already covers by construction.
 */
class StubCatchRecordSubmissionRepository : CatchRecordSubmissionRepository {
    override suspend fun submit(draft: CatchRecordDraft): Result<Unit> = Result.success(Unit)
}
