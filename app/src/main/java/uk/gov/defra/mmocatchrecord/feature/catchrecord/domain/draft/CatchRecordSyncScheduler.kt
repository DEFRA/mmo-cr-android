package uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft

/**
 * Schedules a background retry of a queued (offline) catch-record submission once connectivity returns.
 * Abstracts WorkManager (see
 * `uk.gov.defra.mmocatchrecord.feature.catchrecord.data.sync.WorkManagerCatchRecordSyncScheduler` and
 * ADR 0009) behind a plain interface so `CatchRecordFlowViewModel` stays unit-testable with a fake, with
 * no WorkManager/Android dependency in its test classpath.
 */
interface CatchRecordSyncScheduler {
    /** Enqueues sync work for the [draftId]; a no-op if work for it is already enqueued or running. */
    fun scheduleSync(draftId: String)
}
