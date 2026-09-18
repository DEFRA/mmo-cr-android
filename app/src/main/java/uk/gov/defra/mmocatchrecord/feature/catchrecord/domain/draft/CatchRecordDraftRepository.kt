package uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft

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

    /** Returns the most recently modified active draft across all vessels, or `null` if none exists. */
    suspend fun getAnyActiveDraft(): Result<CatchRecordDraft?>

    /**
     * Returns the draft identified by [draftId] regardless of its status (unlike [getActiveDraft]/
     * [getAnyActiveDraft], which only ever return an *active* draft) — used by `CatchRecordSyncWorker`
     * (Phase 8/ADR 0009) to re-fetch a specific `PendingSync` draft by id in the background.
     */
    suspend fun getDraftById(draftId: String): Result<CatchRecordDraft?>

    /**
     * Returns the vessel's existing active draft if one exists, otherwise creates and persists a new
     * empty draft for the vessel and returns it. Never creates a second concurrent active draft for the
     * same vessel.
     */
    suspend fun startDraft(vesselId: String): Result<CatchRecordDraft>

    /** Persists the full current state of [draft] (an autosave "save and continue" step transition). */
    suspend fun saveDraft(draft: CatchRecordDraft): Result<CatchRecordDraft>

    /** Permanently deletes the draft (and all child rows, via cascading delete) identified by [draftId]. */
    suspend fun deleteDraft(draftId: String): Result<Unit>

    /** Transitions the draft to [DraftStatus.ReadyToSubmit]. Fails if no draft with [draftId] exists. */
    suspend fun markReadyToSubmit(draftId: String): Result<CatchRecordDraft>
}
