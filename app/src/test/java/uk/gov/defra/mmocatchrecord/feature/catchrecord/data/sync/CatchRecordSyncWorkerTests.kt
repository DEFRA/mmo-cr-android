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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote.CatchRecordHttpException
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote.CatchRecordUnauthorizedException
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraftRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraftValidation
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DmyDate
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelection
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelectionMode
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.SpeciesWeightEntry
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.SubmissionFailureReason
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeCatchRecordDraftRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeCatchRecordSubmissionRepository
import java.io.IOException

/**
 * Unit tests for [CatchRecordSyncWorker] using `androidx.work:work-testing`'s [TestListenableWorkerBuilder]
 * (see ADR 0009) — a custom [WorkerFactory] wires the fake repositories in place of Hilt's real
 * `@AssistedInject` factory, since only [CatchRecordSyncWorker.doWork]'s logic is under test here, not the
 * Hilt-Work wiring itself.
 */
@RunWith(RobolectricTestRunner::class)
class CatchRecordSyncWorkerTests {
    private val draftRepository = FakeCatchRecordDraftRepository()

    /**
     * Starts a fresh draft for "vessel-1", forces it to [DraftStatus.PendingSync], and persists it —
     * populated as a [CatchRecordDraftValidation]-complete draft (trip timing, ports, one confirmed gear
     * use with a stat rectangle and a confirmed species, not-landed decision answered) since
     * [CatchRecordSyncWorker.doWork] re-validates completeness before every retry attempt.
     */
    private fun pendingDraft(id: String = "draft-1"): CatchRecordDraft =
        runBlocking {
            val started = draftRepository.startDraft("vessel-1").getOrThrow()
            val pending =
                started.copy(
                    id = id,
                    status = DraftStatus.PendingSync,
                    isTripToday = true,
                    departureDate = DmyDate(1, 1, 2020),
                    returnDate = DmyDate(1, 1, 2020),
                    departurePort = PortSelection("port-hastings", PortSelectionMode.Favourite),
                    returnPort = PortSelection("port-hastings", PortSelectionMode.Favourite),
                    gearUses =
                        listOf(
                            GearUse(
                                id = "gear-use-1",
                                gearTypeId = "gear-seine-nets",
                                statisticalSubRectangleCode = "38E95",
                                confirmedUsedOnTrip = true,
                                speciesWeights =
                                    listOf(
                                        SpeciesWeightEntry(
                                            id = "species-1",
                                            speciesId = "species-cod",
                                            confirmedCaught = true,
                                        ),
                                    ),
                            ),
                        ),
                    notLandedStraightAway = false,
                )
            draftRepository.saveDraft(pending).getOrThrow()
        }

    private fun buildWorker(
        submissionRepository: FakeCatchRecordSubmissionRepository,
        draftId: String?,
        runAttemptCount: Int = 0,
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
            .setRunAttemptCount(runAttemptCount)
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
    fun `AC3 successful submission stamps submittedAt and syncedAt, not just status`() =
        runTest {
            val draft = pendingDraft()
            val submissionRepository = FakeCatchRecordSubmissionRepository(shouldSucceed = true)
            val worker = buildWorker(submissionRepository, draftId = draft.id)

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            val updated = draftRepository.getDraftById(draft.id).getOrThrow()
            assertEquals(DraftStatus.Submitted, updated?.status)
            assertNotNull(updated?.submittedAtEpochMillis)
            assertNotNull(updated?.syncedAtEpochMillis)
        }

    @Test
    fun `a draft failing submission validation fails without retrying`() =
        runTest {
            val draft = pendingDraft().copy(gearUses = emptyList())
            draftRepository.saveDraft(draft).getOrThrow()
            val submissionRepository = FakeCatchRecordSubmissionRepository(shouldSucceed = true)
            val worker = buildWorker(submissionRepository, draftId = draft.id)

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.failure(), result)
            assertEquals(0, submissionRepository.submittedDrafts.size)
        }

    @Test
    fun `a transient (IOException) failure requests a retry and leaves the draft pending sync`() =
        runTest {
            val draft = pendingDraft()
            val submissionRepository =
                FakeCatchRecordSubmissionRepository(shouldSucceed = false, failure = IOException("offline"))
            val worker = buildWorker(submissionRepository, draftId = draft.id)

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.retry(), result)
            val updated = draftRepository.getDraftById(draft.id).getOrThrow()
            assertEquals(DraftStatus.PendingSync, updated?.status)
        }

    @Test
    fun `a terminal (403) failure fails and persists SubmissionFailed with its reason`() =
        runTest {
            val draft = pendingDraft()
            val forbidden =
                CatchRecordHttpException(
                    operation = "submit",
                    httpStatusCode = HTTP_FORBIDDEN,
                    serverCode = null,
                    correlationId = null,
                    retryable = false,
                )
            val submissionRepository = FakeCatchRecordSubmissionRepository(shouldSucceed = false, failure = forbidden)
            val worker = buildWorker(submissionRepository, draftId = draft.id)

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.failure(), result)
            val updated = draftRepository.getDraftById(draft.id).getOrThrow()
            assertEquals(DraftStatus.SubmissionFailed, updated?.status)
            assertEquals(SubmissionFailureReason.NotAuthorisedForVessel.name, updated?.submissionFailureReason)
        }

    @Test
    fun `runAttemptCount at the cap turns a still-transient failure terminal`() =
        runTest {
            val draft = pendingDraft()
            val submissionRepository =
                FakeCatchRecordSubmissionRepository(shouldSucceed = false, failure = IOException("offline"))
            val worker = buildWorker(submissionRepository, draftId = draft.id, runAttemptCount = MAX_ATTEMPTS_CAP)

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.failure(), result)
            val updated = draftRepository.getDraftById(draft.id).getOrThrow()
            assertEquals(DraftStatus.SubmissionFailed, updated?.status)
            assertEquals(SubmissionFailureReason.RetryLimitReached.name, updated?.submissionFailureReason)
        }

    @Test
    fun `a 401 retries once silently, then fails terminally as NeedsSignIn`() =
        runTest {
            val draft = pendingDraft()
            val submissionRepository =
                FakeCatchRecordSubmissionRepository(
                    shouldSucceed = false,
                    failure = CatchRecordUnauthorizedException(operation = "submit", correlationId = null),
                )
            val worker = buildWorker(submissionRepository, draftId = draft.id)

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.failure(), result)
            // Two submit() attempts: the original call plus the one silent-refresh retry (ADR 0026).
            assertEquals(2, submissionRepository.submittedDrafts.size)
            val updated = draftRepository.getDraftById(draft.id).getOrThrow()
            assertEquals(DraftStatus.SubmissionFailed, updated?.status)
            assertEquals(SubmissionFailureReason.NeedsSignIn.name, updated?.submissionFailureReason)
        }

    @Test
    fun `S6 a saveDraft failure while persisting the terminal state requests a retry, not a dropped failure`() =
        runTest {
            val draft = pendingDraft()
            val flakyRepository = FailOnStatusDraftRepository(draftRepository, DraftStatus.SubmissionFailed)
            val forbidden =
                CatchRecordHttpException(
                    operation = "submit",
                    httpStatusCode = HTTP_FORBIDDEN,
                    serverCode = null,
                    correlationId = null,
                    retryable = false,
                )
            val submissionRepository = FakeCatchRecordSubmissionRepository(shouldSucceed = false, failure = forbidden)
            val context = ApplicationProvider.getApplicationContext<Context>()
            val inputData = Data.Builder().putString(CatchRecordSyncWorker.KEY_DRAFT_ID, draft.id).build()
            val factory =
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ): ListenableWorker =
                        CatchRecordSyncWorker(appContext, workerParameters, flakyRepository, submissionRepository)
                }
            val worker =
                TestListenableWorkerBuilder<CatchRecordSyncWorker>(context, inputData = inputData)
                    .setWorkerFactory(factory)
                    .build()

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.retry(), result)
        }

    @Test
    fun `B1 a successful submission retains progress persisted during submit, not just status`() =
        runTest {
            val draft = pendingDraft()
            val submissionRepository =
                FakeCatchRecordSubmissionRepository(
                    shouldSucceed = true,
                    onSubmit = { current ->
                        draftRepository
                            .saveDraft(current.copy(serverCatchRecordId = "cr-1", submissionStep = "submitted"))
                            .getOrThrow()
                    },
                )
            val worker = buildWorker(submissionRepository, draftId = draft.id)

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            val updated = draftRepository.getDraftById(draft.id).getOrThrow()
            assertEquals(DraftStatus.Submitted, updated?.status)
            assertEquals("cr-1", updated?.serverCatchRecordId)
            assertEquals("submitted", updated?.submissionStep)
        }

    /** Re-review finding #1: a reload *exception* during failure handling must retry, never fall back to
     *  the stale pre-submit [draft] (which would wipe already-persisted progress on the next write). */
    @Test
    fun `B1 a reload exception while handling a failure requests a retry, not a stale-draft write`() =
        runTest {
            val draft = pendingDraft()
            val forbidden =
                CatchRecordHttpException(
                    operation = "submit",
                    httpStatusCode = HTTP_FORBIDDEN,
                    serverCode = null,
                    correlationId = null,
                    retryable = false,
                )
            val submissionRepository = FakeCatchRecordSubmissionRepository(shouldSucceed = false, failure = forbidden)
            val flakyRepository = FailReloadDraftRepository(draftRepository)
            val context = ApplicationProvider.getApplicationContext<Context>()
            val inputData = Data.Builder().putString(CatchRecordSyncWorker.KEY_DRAFT_ID, draft.id).build()
            val factory =
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ): ListenableWorker =
                        CatchRecordSyncWorker(appContext, workerParameters, flakyRepository, submissionRepository)
                }
            val worker =
                TestListenableWorkerBuilder<CatchRecordSyncWorker>(context, inputData = inputData)
                    .setWorkerFactory(factory)
                    .build()

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.retry(), result)
            val updated = draftRepository.getDraftById(draft.id).getOrThrow()
            assertEquals(DraftStatus.PendingSync, updated?.status)
        }

    /** Re-review finding #1: a reload that finds the row gone must fail terminally — nothing to update. */
    @Test
    fun `B1 a reload returning no row while handling a failure fails without a stale-draft write`() =
        runTest {
            val draft = pendingDraft()
            val forbidden =
                CatchRecordHttpException(
                    operation = "submit",
                    httpStatusCode = HTTP_FORBIDDEN,
                    serverCode = null,
                    correlationId = null,
                    retryable = false,
                )
            val submissionRepository =
                FakeCatchRecordSubmissionRepository(
                    shouldSucceed = false,
                    failure = forbidden,
                    onSubmit = { current -> draftRepository.deleteDraft(current.id).getOrThrow().let { current } },
                )
            val worker = buildWorker(submissionRepository, draftId = draft.id)

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.failure(), result)
            assertNull(draftRepository.getDraftById(draft.id).getOrThrow())
        }

    @Test
    fun `B1 a terminal failure persists the latest reloaded draft, not a stale in-memory snapshot`() =
        runTest {
            val draft = pendingDraft()
            val forbidden =
                CatchRecordHttpException(
                    operation = "submit",
                    httpStatusCode = HTTP_FORBIDDEN,
                    serverCode = null,
                    correlationId = null,
                    retryable = false,
                )
            // Simulates RealCatchRecordSubmissionRepository persisting progress before an overall failure
            // (ADR 0026) — the worker must base its terminal write on this row, not the stale instance.
            val submissionRepository =
                FakeCatchRecordSubmissionRepository(
                    shouldSucceed = false,
                    failure = forbidden,
                    onSubmit = { current ->
                        draftRepository.saveDraft(current.copy(serverCatchRecordId = "cr-progressed")).getOrThrow()
                    },
                )
            val worker = buildWorker(submissionRepository, draftId = draft.id)

            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.failure(), result)
            val updated = draftRepository.getDraftById(draft.id).getOrThrow()
            assertEquals("cr-progressed", updated?.serverCatchRecordId)
            assertEquals(DraftStatus.SubmissionFailed, updated?.status)
        }
}

private const val HTTP_FORBIDDEN = 403
private const val MAX_ATTEMPTS_CAP = CatchRecordSyncWorker.MAX_ATTEMPTS

/** S6 test support: fails only the [saveDraft] call that would persist [failOnStatus], else delegates. */
private class FailOnStatusDraftRepository(
    private val delegate: FakeCatchRecordDraftRepository,
    private val failOnStatus: DraftStatus,
) : CatchRecordDraftRepository by delegate {
    override suspend fun saveDraft(draft: CatchRecordDraft): Result<CatchRecordDraft> =
        if (draft.status == failOnStatus) {
            Result.failure(java.io.IOException("Simulated disk failure"))
        } else {
            delegate.saveDraft(draft)
        }
}

/** B1 test support: the worker's own initial lookup succeeds, but every reload after that (i.e. the
 *  failure-handling reload under test) fails, so a stale-fallback regression would be caught. */
private class FailReloadDraftRepository(
    private val delegate: FakeCatchRecordDraftRepository,
) : CatchRecordDraftRepository by delegate {
    private var callCount = 0

    override suspend fun getDraftById(draftId: String): Result<CatchRecordDraft?> {
        callCount++
        return if (callCount > 1) {
            Result.failure(java.io.IOException("Simulated reload failure"))
        } else {
            delegate.getDraftById(draftId)
        }
    }
}
