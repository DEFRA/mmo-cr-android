@file:Suppress("detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.trip

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.gov.defra.mmocatchrecord.common.design.UnsavedChangesDialogTestTags
import uk.gov.defra.mmocatchrecord.common.navigation.CatchRecordGraphRoute
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.core.connectivity.NetworkConnectivityChecker
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.StubReferenceDataRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraftRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordSubmissionRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordSyncScheduler
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DmyDate
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowEvent
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowViewModel
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.DepartureDateRoute
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.ReturnDateRoute
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.VesselSelectionRoute
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardStep
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardTestTheme
import uk.gov.defra.mmocatchrecord.feature.home.domain.CatchRecordSummary

/**
 * BR-XX (ADR 0014, Phase D) end-to-end journey: unlike [WizardDateStepContentTests] (which exercises the
 * pure content composable directly), this drives the real [DepartureDateScreen] wired to a real
 * [CatchRecordFlowViewModel] inside a [NavHost], proving the dirty-tracking/dialog wiring through the
 * actual screen, not just its stateless content.
 */
@RunWith(RobolectricTestRunner::class)
class DepartureDateScreenUnsavedChangesJourneyTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    private class FakeDraftRepository(
        seed: CatchRecordDraft,
    ) : CatchRecordDraftRepository {
        private val drafts = mutableMapOf(seed.id to seed)

        override suspend fun getActiveDraft(vesselId: String): Result<CatchRecordDraft?> =
            Result.success(drafts.values.firstOrNull { it.vesselId == vesselId && it.status.isActive })

        override suspend fun getAnyActiveDraft(): Result<CatchRecordDraft?> =
            Result.success(drafts.values.firstOrNull { it.status.isActive })

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

        fun currentDepartureDate(draftId: String): DmyDate? = drafts[draftId]?.departureDate
    }

    private class NoopSubmissionRepository : CatchRecordSubmissionRepository {
        override suspend fun submit(draft: CatchRecordDraft): Result<Unit> =
            Result.failure(UnsupportedOperationException("Not exercised by this test"))
    }

    private class NoopSyncScheduler : CatchRecordSyncScheduler {
        override fun scheduleSync(draftId: String) = Unit
    }

    private fun seedDraft(departureDate: DmyDate? = DmyDate(3, 7, 2026)) =
        CatchRecordDraft(
            id = "draft-1",
            vesselId = "vessel-achilles",
            departureDate = departureDate,
            status = DraftStatus.Draft,
            modifiedAtEpochMillis = 0L,
        )

    private fun buildViewModel(draftRepository: CatchRecordDraftRepository): CatchRecordFlowViewModel =
        CatchRecordFlowViewModel(
            draftRepository,
            StubReferenceDataRepository(),
            NoopSubmissionRepository(),
            object : NetworkConnectivityChecker {
                override fun isConnected(): Boolean = true
            },
            NoopSyncScheduler(),
            { 0L },
            { "generated-id" },
            Dispatchers.Default,
        )

    @Composable
    private fun JourneyHarness(
        viewModel: CatchRecordFlowViewModel,
        navController: NavHostController,
    ) {
        WizardTestTheme {
            NavHost(navController = navController, startDestination = CatchRecordGraphRoute) {
                navigation<CatchRecordGraphRoute>(startDestination = VesselSelectionRoute) {
                    composable<VesselSelectionRoute> {
                        Box(modifier = Modifier.testTag("placeholder_vessel_selection"))
                    }
                    composable<DepartureDateRoute> {
                        DepartureDateScreen(
                            viewModel = viewModel,
                            onNavigate = { step ->
                                if (step == WizardStep.ReturnDate) navController.navigate(ReturnDateRoute)
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable<ReturnDateRoute> {
                        Box(modifier = Modifier.testTag("placeholder_return_date"))
                    }
                }
            }
        }
    }

    private fun launchOnDepartureDateScreen(viewModel: CatchRecordFlowViewModel): NavHostController {
        lateinit var navController: NavHostController
        composeTestRule.setContent {
            navController = rememberNavController()
            JourneyHarness(viewModel = viewModel, navController = navController)
        }

        viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
        composeTestRule.waitUntil(timeoutMillis = 5_000) { viewModel.state.value.status is UiStatus.Content<*> }
        composeTestRule.waitForIdle()
        navController.navigate(DepartureDateRoute)
        composeTestRule.waitForIdle()
        return navController
    }

    @Test
    fun cleanStateBackPressHasNoDialogAndNavigationProceeds() {
        val draftRepository = FakeDraftRepository(seedDraft())
        val viewModel = buildViewModel(draftRepository)
        val navController = launchOnDepartureDateScreen(viewModel)

        composeTestRule.onNodeWithText("Back").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(UnsavedChangesDialogTestTags.DIALOG).assertDoesNotExist()
        assertTrue(navController.currentDestination?.hasRoute<VesselSelectionRoute>() == true)
    }

    @Test
    fun dirtyStateBackPressThenStayKeepsDataIntactAndDismissesDialog() {
        val draftRepository = FakeDraftRepository(seedDraft())
        val viewModel = buildViewModel(draftRepository)
        launchOnDepartureDateScreen(viewModel)

        composeTestRule.onNodeWithTag(DepartureDateScreenTestTags.DAY_FIELD).performTextClearance()
        composeTestRule.onNodeWithTag(DepartureDateScreenTestTags.DAY_FIELD).performTextInput("04")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Back").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(UnsavedChangesDialogTestTags.DIALOG).assertIsDisplayed()

        composeTestRule.onNodeWithTag(UnsavedChangesDialogTestTags.STAY_ACTION).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(UnsavedChangesDialogTestTags.DIALOG).assertDoesNotExist()
        composeTestRule.onNodeWithTag(DepartureDateScreenTestTags.SCREEN).assertIsDisplayed()
        composeTestRule.onNodeWithTag(DepartureDateScreenTestTags.DAY_FIELD).assertIsDisplayed()
    }

    @Test
    fun dirtyStateBackPressThenLeaveNavigatesAwayAndDiscardsTheEdit() {
        val draftRepository = FakeDraftRepository(seedDraft())
        val viewModel = buildViewModel(draftRepository)
        val navController = launchOnDepartureDateScreen(viewModel)

        composeTestRule.onNodeWithTag(DepartureDateScreenTestTags.DAY_FIELD).performTextClearance()
        composeTestRule.onNodeWithTag(DepartureDateScreenTestTags.DAY_FIELD).performTextInput("04")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Back").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(UnsavedChangesDialogTestTags.LEAVE_ACTION).performClick()
        composeTestRule.waitForIdle()

        assertTrue(navController.currentDestination?.hasRoute<VesselSelectionRoute>() == true)
        assertEquals(DmyDate(3, 7, 2026), draftRepository.currentDepartureDate("draft-1"))
    }

    @Test
    fun afterSaveAndContinueReturningToTheScreenIsCleanWithNoDialogOnBack() {
        val draftRepository = FakeDraftRepository(seedDraft(departureDate = null))
        val viewModel = buildViewModel(draftRepository)
        val navController = launchOnDepartureDateScreen(viewModel)

        composeTestRule.onNodeWithTag(DepartureDateScreenTestTags.DAY_FIELD).performTextInput("11")
        composeTestRule.onNodeWithTag(DepartureDateScreenTestTags.MONTH_FIELD).performTextInput("09")
        composeTestRule.onNodeWithTag(DepartureDateScreenTestTags.YEAR_FIELD).performTextInput("2026")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(DepartureDateScreenTestTags.SAVE_ACTION).performClick()
        composeTestRule.waitForIdle()

        assertTrue(navController.currentDestination?.hasRoute<ReturnDateRoute>() == true)
        assertEquals(DmyDate(11, 9, 2026), draftRepository.currentDepartureDate("draft-1"))

        navController.popBackStack()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(DepartureDateScreenTestTags.SCREEN).assertIsDisplayed()

        composeTestRule.onNodeWithText("Back").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(UnsavedChangesDialogTestTags.DIALOG).assertDoesNotExist()
        assertTrue(navController.currentDestination?.hasRoute<VesselSelectionRoute>() == true)
    }
}
