package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow

import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordSyncScheduler

/** Test double for [CatchRecordSyncScheduler] — records scheduled draft ids instead of touching WorkManager. */
class FakeCatchRecordSyncScheduler : CatchRecordSyncScheduler {
    var scheduledDraftIds: List<String> = emptyList()
        private set

    /** Throws [throwOnNextScheduleSync] from the next [scheduleSync] call instead of returning, then
     *  clears itself. */
    var throwOnNextScheduleSync: Throwable? = null

    override fun scheduleSync(draftId: String) {
        throwOnNextScheduleSync?.let {
            throwOnNextScheduleSync = null
            throw it
        }
        scheduledDraftIds = scheduledDraftIds + draftId
    }
}
