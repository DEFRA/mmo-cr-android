package uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft

import uk.gov.defra.mmocatchrecord.core.connectivity.NetworkConnectivityChecker
import javax.inject.Inject

/** Outcome of [RetryCatchRecordSubmissionUseCase] (FR8/AC4) — both cases enqueue; only the UI copy differs. */
sealed interface RetryCatchRecordSubmissionResult {
    data object Enqueued : RetryCatchRecordSubmissionResult

    data object EnqueuedWhileOffline : RetryCatchRecordSubmissionResult
}

/**
 * Manual retry (FR8, CRAR-152 Phase C). Always enqueues via [CatchRecordSyncScheduler], even offline —
 * see ADR 0014 Phase C.
 */
class RetryCatchRecordSubmissionUseCase
    @Inject
    constructor(
        private val syncScheduler: CatchRecordSyncScheduler,
        private val connectivityChecker: NetworkConnectivityChecker,
    ) {
        operator fun invoke(draftId: String): RetryCatchRecordSubmissionResult {
            syncScheduler.scheduleSync(draftId)
            return if (connectivityChecker.isConnected()) {
                RetryCatchRecordSubmissionResult.Enqueued
            } else {
                RetryCatchRecordSubmissionResult.EnqueuedWhileOffline
            }
        }
    }
