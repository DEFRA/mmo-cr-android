package uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft

import kotlinx.coroutines.flow.Flow
import uk.gov.defra.mmocatchrecord.feature.home.domain.CatchRecordSummary

/**
 * Repository abstraction over catch-record draft persistence. Offline-first: every write is accepted
 * locally first (no network dependency for this feature — see ADR 0006), and every function returns a
 * [Result] so callers must handle failure explicitly rather than assume success.
 *
 * **Draft singularity:** at most one **active** (non-terminal-status, see [DraftStatus.isActive]) draft
 * may exist per vessel at a time. [startDraft] enforces this by returning the existing active draft
 * instead of creating a duplicate.
 */
interface CatchRecordDraftRepository {
    /** Returns the vessel's current active draft, or `null` (wrapped in success) if none exists. */
    suspend fun getActiveDraft(vesselId: String): Result<CatchRecordDraft?>

    /**
     * Returns the draft identified by [draftId] regardless of its status (unlike [getActiveDraft], which
     * only ever returns an *active* draft) — used by the Home records list's resume action (FR2) and by
     * `CatchRecordSyncWorker` (Phase 8/ADR 0009) to re-fetch a specific `PendingSync` draft by id.
     */
    suspend fun getDraftById(draftId: String): Result<CatchRecordDraft?>

    /**
     * Persists [candidate] unless the vessel already has an active draft (then returns that unchanged) —
     * see ADR 0010 for race-safety and ADR 0014 for why callers build [candidate] (BR-XX).
     */
    suspend fun startDraft(candidate: CatchRecordDraft): Result<CatchRecordDraft>

    /** Persists the full current state of [draft] (an autosave "save and continue" step transition). */
    suspend fun saveDraft(draft: CatchRecordDraft): Result<CatchRecordDraft>

    /** Permanently deletes the draft (and all child rows, via cascading delete) identified by [draftId]. */
    suspend fun deleteDraft(draftId: String): Result<Unit>

    /** Transitions the draft to [DraftStatus.ReadyToSubmit]. Fails if no draft with [draftId] exists. */
    suspend fun markReadyToSubmit(draftId: String): Result<CatchRecordDraft>

    /** Reactive list-projection summaries for the Home records list (CRAR-152 Phase B); excludes Discarded. */
    fun observeRecordSummaries(): Flow<List<CatchRecordSummary>>

    /** Distinct port ids from the vessel's submitted trips, most recent first; empty if it has no history. */
    suspend fun getPreviouslyUsedPortIds(vesselId: String): Result<List<String>> = Result.success(emptyList())
}
