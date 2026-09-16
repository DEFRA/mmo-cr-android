package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard

import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordSyncScheduler

/** Test double for [CatchRecordSyncScheduler] — records scheduled draft ids instead of touching WorkManager. */
class FakeCatchRecordSyncScheduler : CatchRecordSyncScheduler {
    var scheduledDraftIds: List<String> = emptyList()
        private set

    override fun scheduleSync(draftId: String) {
        scheduledDraftIds = scheduledDraftIds + draftId
    }
}
