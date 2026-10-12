package uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft

import org.junit.Assert.assertEquals
import org.junit.Test
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeCatchRecordSyncScheduler
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeNetworkConnectivityChecker

/** Covers FR8/AC4: both the online and offline retry path must enqueue (ADR 0014 Phase C). */
class RetryCatchRecordSubmissionUseCaseTests {
    @Test
    fun `online retry enqueues the draft and returns Enqueued`() {
        val scheduler = FakeCatchRecordSyncScheduler()
        val useCase = RetryCatchRecordSubmissionUseCase(scheduler, FakeNetworkConnectivityChecker(connected = true))

        val result = useCase("draft-1")

        assertEquals(listOf("draft-1"), scheduler.scheduledDraftIds)
        assertEquals(RetryCatchRecordSubmissionResult.Enqueued, result)
    }

    @Test
    fun `offline retry still enqueues the draft and signals EnqueuedWhileOffline`() {
        val scheduler = FakeCatchRecordSyncScheduler()
        val useCase = RetryCatchRecordSubmissionUseCase(scheduler, FakeNetworkConnectivityChecker(connected = false))

        val result = useCase("draft-2")

        assertEquals(listOf("draft-2"), scheduler.scheduledDraftIds)
        assertEquals(RetryCatchRecordSubmissionResult.EnqueuedWhileOffline, result)
    }
}
