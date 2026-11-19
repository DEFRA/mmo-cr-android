package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.submission

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.core.connectivity.NetworkConnectivityChecker
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.StubReferenceDataRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraftRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordSubmissionRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordSyncScheduler
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.SubmissionFailureReason
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowEvent
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowViewModel
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardStep
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardTestTheme
import uk.gov.defra.mmocatchrecord.feature.home.domain.CatchRecordSummary
import java.io.IOException

/** Drives [SubmissionFailedScreen] through a real flow ViewModel: load error, content and "Try again". */
@RunWith(RobolectricTestRunner::class)
class SubmissionFailedScreenTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    private class InMemoryDraftRepository(
        seed: CatchRecordDraft?,
    ) : CatchRecordDraftRepository {
        val drafts = listOfNotNull(seed).associateBy { it.id }.toMutableMap()

        override suspend fun getDraftById(draftId: String): Result<CatchRecordDraft?> =
            if (drafts.isEmpty()) Result.failure(IOException("Simulated failure")) else Result.success(drafts[draftId])

        override suspend fun startDraft(candidate: CatchRecordDraft): Result<CatchRecordDraft> =
            Result.failure(UnsupportedOperationException("Not exercised by this test"))

        override suspend fun saveDraft(draft: CatchRecordDraft): Result<CatchRecordDraft> {
            drafts[draft.id] = draft
            return Result.success(draft)
        }

        override suspend fun deleteDraft(draftId: String): Result<Unit> = Result.success(Unit)

        override suspend fun markReadyToSubmit(draftId: String): Result<CatchRecordDraft> =
            Result.failure(UnsupportedOperationException("Not exercised by this test"))

        override fun observeRecordSummaries(): Flow<List<CatchRecordSummary>> = flowOf(emptyList())
    }

    private class RecordingSyncScheduler : CatchRecordSyncScheduler {
        val scheduledDraftIds = mutableListOf<String>()

        override fun scheduleSync(draftId: String) {
            scheduledDraftIds += draftId
        }
    }

    private val failedDraft =
        CatchRecordDraft(
            id = "draft-1",
            vesselId = "vessel-1",
            modifiedAtEpochMillis = 0L,
            status = DraftStatus.SubmissionFailed,
            submissionFailureReason = SubmissionFailureReason.TooLarge.name,
            catchRecordReference = "GBR-B14974-270726-150815",
        )

    private fun buildViewModel(
        draftRepository: CatchRecordDraftRepository,
        syncScheduler: CatchRecordSyncScheduler = RecordingSyncScheduler(),
    ) = CatchRecordFlowViewModel(
        draftRepository,
        StubReferenceDataRepository(),
        CatchRecordSubmissionRepository { Result.success(it) },
        NetworkConnectivityChecker { true },
        syncScheduler,
        { 0L },
        { "generated-id" },
        Dispatchers.Default,
    )

    private fun setScreen(
        viewModel: CatchRecordFlowViewModel,
        onNavigate: (WizardStep) -> Unit = {},
    ) {
        composeTestRule.setContent {
            WizardTestTheme {
                SubmissionFailedScreen(viewModel = viewModel, onNavigate = onNavigate, onBack = {})
            }
        }
    }

    @Test
    fun `shows the error state when loading the draft fails`() {
        val viewModel = buildViewModel(InMemoryDraftRepository(seed = null))
        setScreen(viewModel)

        viewModel.dispatch(CatchRecordFlowEvent.EnterFlowForDraft("draft-1"))
        composeTestRule.waitUntil(timeoutMillis = 5_000) { viewModel.state.value.status is UiStatus.Error }

        composeTestRule.onNodeWithTag(SubmissionFailedScreenTestTags.ERROR_MESSAGE).assertIsDisplayed()
    }

    @Test
    fun `try again re-queues the failed draft for sync and navigates to pending sync once`() {
        val draftRepository = InMemoryDraftRepository(failedDraft)
        val syncScheduler = RecordingSyncScheduler()
        val viewModel = buildViewModel(draftRepository, syncScheduler)
        val navigatedTo = mutableListOf<WizardStep>()
        setScreen(viewModel) { navigatedTo += it }

        viewModel.dispatch(CatchRecordFlowEvent.EnterFlowForDraft("draft-1"))
        composeTestRule.waitUntil(timeoutMillis = 5_000) { viewModel.state.value.status is UiStatus.Content<*> }
        composeTestRule.onNodeWithTag(SubmissionFailedScreenTestTags.BANNER).assertIsDisplayed()
        composeTestRule.onNodeWithTag(SubmissionFailedScreenTestTags.RETRY_ACTION).performScrollTo().performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) { navigatedTo.isNotEmpty() }
        composeTestRule.waitForIdle()

        assertEquals(listOf(WizardStep.SubmissionPendingSync), navigatedTo)
        assertEquals(listOf("draft-1"), syncScheduler.scheduledDraftIds)
        assertEquals(DraftStatus.PendingSync, draftRepository.drafts.getValue("draft-1").status)
    }
}
