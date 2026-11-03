package uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft

import kotlinx.coroutines.flow.Flow
import uk.gov.defra.mmocatchrecord.feature.home.domain.CatchRecordSummary

/**
 * Repository abstraction over catch-record draft persistence. Offline-first: every write is accepted
 * locally first (no network dependency for this feature — see ADR 0006), and every function returns a
 * [Result] so callers must handle failure explicitly rather than assume success.
 *
 * **Draft independence:** a vessel may hold any number of simultaneous active drafts, and no API here
 * resolves a draft by vessel — a draft is only ever addressed by its own id (ADR 0018).
 */
interface CatchRecordDraftRepository {
    /** Returns the draft identified by [draftId] regardless of status — used by the Home records list's
     *  resume action (FR2) and `CatchRecordSyncWorker` to re-fetch a `PendingSync` draft by id. */
    suspend fun getDraftById(draftId: String): Result<CatchRecordDraft?>

    /**
     * Persists [candidate] as a new, independent draft and never returns an existing one (ADR 0018) —
     * see ADR 0014 for why callers build [candidate] (BR-XX).
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
