package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.FakeCatchRecordDraftRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.FakeCatchRecordSubmissionRepository

/**
 * Unit tests for [CatchRecordSyncWorker] using `androidx.work:work-testing`'s [TestListenableWorkerBuilder]
 * (see ADR 0009) — a custom [WorkerFactory] wires the fake repositories in place of Hilt's real
 * `@AssistedInject` factory, since only [CatchRecordSyncWorker.doWork]'s logic is under test here, not the
 * Hilt-Work wiring itself.
 */
@RunWith(RobolectricTestRunner::class)
class CatchRecordSyncWorkerTests {
    private val draftRepository = FakeCatchRecordDraftRepository()

    /** Starts a fresh draft for "vessel-1", forces it to [DraftStatus.PendingSync], and persists it. */
    private fun pendingDraft(id: String = "draft-1"): CatchRecordDraft =
        runBlocking {
            val started = draftRepository.startDraft("vessel-1").getOrThrow()
            val pending = started.copy(id = id, status = DraftStatus.PendingSync)
            draftRepository.saveDraft(pending).getOrThrow()
        }

    private fun buildWorker(
        submissionRepository: FakeCatchRecordSubmissionRepository,
        draftId: String?,
    ): CatchRecordSyncWorker {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val inputData =
            if (draftId != null) {
                Data.Builder().putString(CatchRecordSyncWorker.KEY_DRAFT_ID, draftId).build()
            } else {
                Data.EMPTY
            }
        val factory =
            object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker =
                    CatchRecordSyncWorker(appContext, workerParameters, draftRepository, submissionRepository)
            }
        return TestListenableWorkerBuilder<CatchRecordSyncWorker>(context, inputData = inputData)
            .setWorkerFactory(factory)
            .build()
    }

    @Test
    fun `missing draft id input fails without attempting a submission`() =
        runTest {
            val submissionRepository = FakeCatchRecordSubmissionRepository()
            val worker = buildWorker(submissionRepository, draftId = null)

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.failure(), result)
            assertEquals(0, submissionRepository.submittedDrafts.size)
        }

    @Test
    fun `unknown draft id fails without attempting a submission`() =
        runTest {
            val submissionRepository = FakeCatchRecordSubmissionRepository()
            val worker = buildWorker(submissionRepository, draftId = "unknown-draft")

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.failure(), result)
            assertEquals(0, submissionRepository.submittedDrafts.size)
        }

    @Test
    fun `a draft no longer pending sync succeeds without re-submitting`() =
        runTest {
            val draft = pendingDraft()
            draftRepository.saveDraft(draft.copy(status = DraftStatus.Submitted)).getOrThrow()
            val submissionRepository = FakeCatchRecordSubmissionRepository()
            val worker = buildWorker(submissionRepository, draftId = draft.id)

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            assertEquals(0, submissionRepository.submittedDrafts.size)
        }

    @Test
    fun `successful submission updates the draft status to Submitted and succeeds`() =
        runTest {
            val draft = pendingDraft()
            val submissionRepository = FakeCatchRecordSubmissionRepository(shouldSucceed = true)
            val worker = buildWorker(submissionRepository, draftId = draft.id)

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            assertEquals(1, submissionRepository.submittedDrafts.size)
            val updated = draftRepository.getDraftById(draft.id).getOrThrow()
            assertEquals(DraftStatus.Submitted, updated?.status)
        }

    @Test
    fun `a failed submission requests a retry and leaves the draft pending sync`() =
        runTest {
            val draft = pendingDraft()
            val submissionRepository = FakeCatchRecordSubmissionRepository(shouldSucceed = false)
            val worker = buildWorker(submissionRepository, draftId = draft.id)

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.retry(), result)
            val updated = draftRepository.getDraftById(draft.id).getOrThrow()
            assertEquals(DraftStatus.PendingSync, updated?.status)
        }
}
