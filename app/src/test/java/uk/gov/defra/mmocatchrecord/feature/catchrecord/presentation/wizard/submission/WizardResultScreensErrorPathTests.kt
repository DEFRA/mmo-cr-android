@file:Suppress("detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.submission

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performFirstLinkClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DmyDate
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.MeasurementValue
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelection
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelectionMode
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.SpeciesWeightEntry
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldKeys
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowEvent
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowViewModel
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardStep
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardTestTheme
import uk.gov.defra.mmocatchrecord.feature.home.domain.CatchRecordSummary
import java.io.IOException

/** Drives a real flow ViewModel to reach result-wrapper error branches absent from content-only tests.
 * Also covers the late-submission warning wrapper. */
@RunWith(RobolectricTestRunner::class)
class WizardResultScreensErrorPathTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    private class FailingDraftRepository : CatchRecordDraftRepository {
        override suspend fun getActiveDraft(vesselId: String): Result<CatchRecordDraft?> = failure()

        override suspend fun getDraftById(draftId: String): Result<CatchRecordDraft?> = failure()

        override suspend fun startDraft(candidate: CatchRecordDraft): Result<CatchRecordDraft> = failure()

        override suspend fun saveDraft(draft: CatchRecordDraft): Result<CatchRecordDraft> = failure()

        override suspend fun deleteDraft(draftId: String): Result<Unit> = failure()

        override suspend fun markReadyToSubmit(draftId: String): Result<CatchRecordDraft> = failure()

        override fun observeRecordSummaries(): Flow<List<CatchRecordSummary>> = flowOf(emptyList())

        private fun <T> failure(): Result<T> = Result.failure(IOException("Simulated failure"))
    }

    private class SeededDraftRepository(
        seed: CatchRecordDraft,
    ) : CatchRecordDraftRepository {
        private val drafts = mutableMapOf(seed.id to seed)

        override suspend fun getActiveDraft(vesselId: String): Result<CatchRecordDraft?> =
            Result.success(drafts.values.firstOrNull { it.vesselId == vesselId && it.status.isActive })

        override suspend fun getDraftById(draftId: String): Result<CatchRecordDraft?> = Result.success(drafts[draftId])

        override suspend fun startDraft(candidate: CatchRecordDraft): Result<CatchRecordDraft> =
            Result.failure(UnsupportedOperationException("Not exercised by this test"))

        override suspend fun saveDraft(draft: CatchRecordDraft): Result<CatchRecordDraft> {
            drafts[draft.id] = draft
            return Result.success(draft)
        }

        override suspend fun deleteDraft(draftId: String): Result<Unit> {
            drafts.remove(draftId)
            return Result.success(Unit)
        }

        override suspend fun markReadyToSubmit(draftId: String): Result<CatchRecordDraft> =
            Result.failure(UnsupportedOperationException("Not exercised by this test"))

        override fun observeRecordSummaries(): Flow<List<CatchRecordSummary>> = flowOf(emptyList())
    }

    private class FakeSubmissionRepository : CatchRecordSubmissionRepository {
        override suspend fun submit(draft: CatchRecordDraft): Result<Unit> = Result.success(Unit)
    }

    private class FakeConnectivityChecker : NetworkConnectivityChecker {
        override fun isConnected(): Boolean = true
    }

    private class RecordingSyncScheduler : CatchRecordSyncScheduler {
        val scheduledDraftIds = mutableListOf<String>()

        override fun scheduleSync(draftId: String) {
            scheduledDraftIds += draftId
        }
    }

    private fun lateDraft(id: String = "draft-1"): CatchRecordDraft =
        CatchRecordDraft(
            id = id,
            vesselId = "vessel-achilles",
            isTripToday = false,
            departureDate = DmyDate(1, 1, 2020),
            returnDate = DmyDate(2, 1, 2020),
            departurePort = PortSelection(portId = "port-hastings", selectionMode = PortSelectionMode.FirstTime),
            returnPort = PortSelection(portId = "port-hastings", selectionMode = PortSelectionMode.FirstTime),
            gearUses =
                listOf(
                    GearUse(
                        id = "gear-use-1",
                        gearTypeId = "gear-seine-nets",
                        statisticalSubRectangleCode = "38E95",
                        measurements =
                            mapOf(GearMeasurementFieldKeys.MESH_SIZE_MM to MeasurementValue.Numeric(50.0, "mm")),
                        speciesWeights =
                            listOf(
                                SpeciesWeightEntry(
                                    id = "sw-1",
                                    speciesId = "species-cod",
                                    weightAboveMinimumSizeKg = 12.5,
                                    confirmedCaught = true,
                                ),
                            ),
                        numberOfShots = 3,
                        confirmedUsedOnTrip = true,
                    ),
                ),
            notLandedStraightAway = false,
            status = DraftStatus.Draft,
            modifiedAtEpochMillis = 0L,
            catchRecordReference = "B14974-02012020-150815",
            lateSubmissionWarningAcknowledged = false,
        )

    private fun buildViewModel(
        draftRepository: CatchRecordDraftRepository,
        syncScheduler: CatchRecordSyncScheduler = RecordingSyncScheduler(),
    ): CatchRecordFlowViewModel =
        CatchRecordFlowViewModel(
            draftRepository,
            StubReferenceDataRepository(),
            FakeSubmissionRepository(),
            FakeConnectivityChecker(),
            syncScheduler,
            { 0L },
            { "generated-id" },
            Dispatchers.Default,
        )

    private fun enterFlowAndAwaitError(viewModel: CatchRecordFlowViewModel) {
        viewModel.dispatch(CatchRecordFlowEvent.EnterFlowForDraft("draft-1"))
        composeTestRule.waitUntil(timeoutMillis = 5_000) { viewModel.state.value.status is UiStatus.Error }
        composeTestRule.waitForIdle()
    }

    private fun enterFlowAndAwaitContent(viewModel: CatchRecordFlowViewModel) {
        viewModel.dispatch(CatchRecordFlowEvent.EnterFlowForDraft("draft-1"))
        composeTestRule.waitUntil(timeoutMillis = 5_000) { viewModel.state.value.status is UiStatus.Content<*> }
        composeTestRule.waitForIdle()
    }

    @Test
    fun `submission success screen shows the error state when loading the draft fails`() {
        val viewModel = buildViewModel(FailingDraftRepository())
        composeTestRule.setContent {
            WizardTestTheme {
                SubmissionSuccessScreen(viewModel = viewModel, onViewRecords = {}, onBack = {})
            }
        }

        enterFlowAndAwaitError(viewModel)

        composeTestRule.onNodeWithTag(SubmissionSuccessScreenTestTags.ERROR_MESSAGE).assertIsDisplayed()
    }

    @Test
    fun `submission pending sync screen shows the error state when loading the draft fails`() {
        val viewModel = buildViewModel(FailingDraftRepository())
        composeTestRule.setContent {
            WizardTestTheme {
                SubmissionPendingSyncScreen(viewModel = viewModel, onViewRecords = {}, onBack = {})
            }
        }

        enterFlowAndAwaitError(viewModel)

        composeTestRule.onNodeWithTag(SubmissionPendingSyncScreenTestTags.ERROR_MESSAGE).assertIsDisplayed()
    }

    @Test
    fun `check your answers screen shows the error state when loading the draft fails`() {
        val viewModel = buildViewModel(FailingDraftRepository())
        composeTestRule.setContent {
            WizardTestTheme {
                CheckYourAnswersScreen(
                    viewModel = viewModel,
                    onNavigate = {},
                    onNavigateToEdit = { _, _ -> },
                    onBack = {},
                )
            }
        }

        enterFlowAndAwaitError(viewModel)

        composeTestRule.onNodeWithTag(CheckYourAnswersScreenTestTags.ERROR_MESSAGE).assertIsDisplayed()
    }

    @Test
    fun `late submission warning screen shows the error state when loading the draft fails`() {
        val viewModel = buildViewModel(FailingDraftRepository())
        composeTestRule.setContent {
            WizardTestTheme {
                LateSubmissionWarningScreen(viewModel = viewModel, onNavigate = {}, onBack = {})
            }
        }

        enterFlowAndAwaitError(viewModel)

        composeTestRule.onNodeWithTag(LateSubmissionWarningScreenTestTags.ERROR_MESSAGE).assertIsDisplayed()
    }

    @Test
    fun `late submission warning screen check trip end date link navigates without saving`() {
        val viewModel = buildViewModel(SeededDraftRepository(lateDraft()))
        var navigatedTo: WizardStep? = null
        composeTestRule.setContent {
            WizardTestTheme {
                LateSubmissionWarningScreen(
                    viewModel = viewModel,
                    onNavigate = { navigatedTo = it },
                    onBack = {},
                )
            }
        }
        enterFlowAndAwaitContent(viewModel)

        composeTestRule
            .onNodeWithTag(LateSubmissionWarningScreenTestTags.CHECK_TRIP_END_DATE_LINK)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(LateSubmissionWarningScreenTestTags.CHECK_TRIP_END_DATE_LINK)
            .performFirstLinkClick()

        assertEquals(WizardStep.ReturnDate, navigatedTo)
    }

    @Test
    fun `late submission warning screen save and continue acknowledges the warning and navigates on`() {
        val syncScheduler = RecordingSyncScheduler()
        val viewModel = buildViewModel(SeededDraftRepository(lateDraft()), syncScheduler)
        var navigatedTo: WizardStep? = null
        composeTestRule.setContent {
            WizardTestTheme {
                LateSubmissionWarningScreen(
                    viewModel = viewModel,
                    onNavigate = { navigatedTo = it },
                    onBack = {},
                )
            }
        }
        enterFlowAndAwaitContent(viewModel)

        composeTestRule.onNodeWithTag(LateSubmissionWarningScreenTestTags.SAVE_ACTION).performClick()
        composeTestRule.waitForIdle()

        assertEquals(WizardStep.CheckYourAnswers, navigatedTo)
        assertNotNull(syncScheduler)
    }
}
