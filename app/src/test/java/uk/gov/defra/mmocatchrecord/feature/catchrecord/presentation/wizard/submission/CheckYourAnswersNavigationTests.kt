@file:Suppress("detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.submission

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.MeasurementValue
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelection
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelectionMode
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.SpeciesWeightEntry
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldKeys
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowEvent
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowViewModel
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CheckYourAnswersRoute
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.GearSpeciesChecklistRoute
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.SubmissionPendingSyncRoute
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.SubmissionSuccessRoute
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.VesselSelectionRoute
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardStep
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardTestTheme
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.applySubmissionResultNavOptions
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.editRouteFor
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.routeFor

/**
 * JVM/Robolectric port of the androidTest `SubmissionFlowNavigationTest` — see that file's doc comments for
 * the full rationale. Coverage from `connectedDebugAndroidTest` is not merged into the Kover/SonarCloud
 * report that backs this PR's quality gate, so the real, ViewModel-backed [CheckYourAnswersScreen] (not
 * just the pure [CheckYourAnswersScreenContent] exercised by [CheckYourAnswersScreenTests]) needs an
 * equivalent `testDebugUnitTest` exerciser — in particular its post-submit navigation effect and its
 * "Change" link branch (`onNavigateToEdit` vs `onNavigate`, keyed on whether the tapped row carries an
 * explicit gear-use edit context).
 */
@RunWith(RobolectricTestRunner::class)
class CheckYourAnswersNavigationTests {
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

        override suspend fun startDraft(vesselId: String): Result<CatchRecordDraft> =
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

        fun statusOf(draftId: String): DraftStatus? = drafts[draftId]?.status
    }

    private class FakeSubmissionRepository(
        private val succeeds: Boolean,
    ) : CatchRecordSubmissionRepository {
        var submitCallCount = 0
            private set

        override suspend fun submit(draft: CatchRecordDraft): Result<Unit> {
            submitCallCount++
            return if (succeeds) Result.success(Unit) else Result.failure(IllegalStateException("Simulated failure"))
        }
    }

    private class FakeConnectivityChecker(
        private val connected: Boolean,
    ) : NetworkConnectivityChecker {
        override fun isConnected(): Boolean = connected
    }

    private class RecordingSyncScheduler : CatchRecordSyncScheduler {
        val scheduledDraftIds = mutableListOf<String>()

        override fun scheduleSync(draftId: String) {
            scheduledDraftIds += draftId
        }
    }

    private fun completeDraft(id: String = "draft-1"): CatchRecordDraft =
        CatchRecordDraft(
            id = id,
            vesselId = "vessel-achilles",
            isTripToday = false,
            departureDate = DmyDate(1, 1, 2026),
            returnDate = DmyDate(2, 1, 2026),
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
            catchRecordReference = "A1234520260727150815",
            lateSubmissionWarningAcknowledged = true,
        )

    @Suppress("LongParameterList")
    private fun buildViewModel(
        draftRepository: CatchRecordDraftRepository,
        submissionRepository: CatchRecordSubmissionRepository,
        connectivityChecker: NetworkConnectivityChecker,
        syncScheduler: CatchRecordSyncScheduler,
    ): CatchRecordFlowViewModel =
        CatchRecordFlowViewModel(
            draftRepository,
            StubReferenceDataRepository(),
            submissionRepository,
            connectivityChecker,
            syncScheduler,
            { 0L },
            { "generated-id" },
            Dispatchers.Default,
        )

    @Composable
    private fun SubmissionFlowHarness(
        viewModel: CatchRecordFlowViewModel,
        navController: NavHostController,
        onChangeRowObserved: (CheckYourAnswersRow) -> Unit = {},
    ) {
        WizardTestTheme {
            NavHost(navController = navController, startDestination = CatchRecordGraphRoute) {
                navigation<CatchRecordGraphRoute>(startDestination = CheckYourAnswersRoute) {
                    composable<CheckYourAnswersRoute> {
                        CheckYourAnswersScreen(
                            viewModel = viewModel,
                            onNavigate = { step ->
                                navController.navigate(routeFor(step)) { applySubmissionResultNavOptions(step) }
                            },
                            onNavigateToEdit = { step, gearUseId ->
                                onChangeRowObserved(
                                    CheckYourAnswersRow(
                                        kind = CheckYourAnswersFieldKind.Species,
                                        value = "",
                                        changeStep = step,
                                        changeGearUseId = gearUseId,
                                    ),
                                )
                                navController.navigate(editRouteFor(step, gearUseId))
                            },
                            onBack = {},
                        )
                    }
                    composable<SubmissionSuccessRoute> {
                        SubmissionSuccessScreen(viewModel = viewModel, onViewRecords = {}, onBack = {})
                    }
                    composable<SubmissionPendingSyncRoute> {
                        SubmissionPendingSyncScreen(viewModel = viewModel, onViewRecords = {}, onBack = {})
                    }
                    // Minimal placeholder destinations (not the real wizard screens, which take their own
                    // additional state/constructor params): just enough for onNavigate/onNavigateToEdit's
                    // real navigate() calls to resolve to a destination that actually exists in this
                    // harness's graph, so the "Change" link branch under test can be exercised end-to-end.
                    composable<VesselSelectionRoute> {
                        Box(modifier = Modifier.testTag("placeholder_vessel_selection"))
                    }
                    composable<GearSpeciesChecklistRoute> {
                        Box(modifier = Modifier.testTag("placeholder_gear_species_checklist"))
                    }
                }
            }
        }
    }

    private fun launchHarnessAndReachCheckYourAnswers(
        viewModel: CatchRecordFlowViewModel,
        onChangeRowObserved: (CheckYourAnswersRow) -> Unit = {},
    ): NavHostController {
        lateinit var navController: NavHostController
        composeTestRule.setContent {
            navController = rememberNavController()
            SubmissionFlowHarness(
                viewModel = viewModel,
                navController = navController,
                onChangeRowObserved = onChangeRowObserved,
            )
        }

        viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
        composeTestRule.waitUntil(timeoutMillis = 5_000) { viewModel.state.value.status is UiStatus.Content<*> }
        composeTestRule.waitForIdle()
        return navController
    }

    private inline fun <reified T : Any> assertRouteClearedFromBackStack(navController: NavHostController) {
        val stillOnBackStack = runCatching { navController.getBackStackEntry<T>() }.isSuccess
        assertTrue(
            "Expected route '${T::class.simpleName}' to have been cleared from the back stack by applySubmissionResultNavOptions",
            !stillOnBackStack,
        )
    }

    @Test
    fun acceptingDeclarationWhileOnlineNavigatesToSubmissionSuccessAndMarksDraftSubmitted() {
        val seedDraft = completeDraft()
        val draftRepository = FakeDraftRepository(seedDraft)
        val submissionRepository = FakeSubmissionRepository(succeeds = true)
        val syncScheduler = RecordingSyncScheduler()
        val viewModel =
            buildViewModel(
                draftRepository = draftRepository,
                submissionRepository = submissionRepository,
                connectivityChecker = FakeConnectivityChecker(connected = true),
                syncScheduler = syncScheduler,
            )

        val navController = launchHarnessAndReachCheckYourAnswers(viewModel)

        composeTestRule
            .onNodeWithTag(CheckYourAnswersScreenTestTags.SUBMIT_ACTION)
            .performScrollTo()
            .performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            navController.currentDestination?.hasRoute<SubmissionSuccessRoute>() == true
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(SubmissionSuccessScreenTestTags.SCREEN).assertIsDisplayed()
        composeTestRule.onNodeWithText("Your catch record reference A1234520260727150815").assertIsDisplayed()

        assertEquals(1, submissionRepository.submitCallCount)
        assertEquals(DraftStatus.Submitted, draftRepository.statusOf(seedDraft.id))
        assertTrue("Online success must not enqueue a background sync", syncScheduler.scheduledDraftIds.isEmpty())

        assertRouteClearedFromBackStack<CheckYourAnswersRoute>(navController)
    }

    @Test
    fun acceptingDeclarationWhileOfflineNavigatesToSubmissionPendingSyncAndEnqueuesBackgroundSync() {
        val seedDraft = completeDraft()
        val draftRepository = FakeDraftRepository(seedDraft)
        val submissionRepository = FakeSubmissionRepository(succeeds = true)
        val syncScheduler = RecordingSyncScheduler()
        val viewModel =
            buildViewModel(
                draftRepository = draftRepository,
                submissionRepository = submissionRepository,
                connectivityChecker = FakeConnectivityChecker(connected = false),
                syncScheduler = syncScheduler,
            )

        val navController = launchHarnessAndReachCheckYourAnswers(viewModel)

        composeTestRule
            .onNodeWithTag(CheckYourAnswersScreenTestTags.SUBMIT_ACTION)
            .performScrollTo()
            .performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            navController.currentDestination?.hasRoute<SubmissionPendingSyncRoute>() == true
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(SubmissionPendingSyncScreenTestTags.SCREEN).assertIsDisplayed()
        composeTestRule.onNodeWithText("Your catch record reference A1234520260727150815").assertIsDisplayed()

        assertEquals(0, submissionRepository.submitCallCount)
        assertEquals(DraftStatus.PendingSync, draftRepository.statusOf(seedDraft.id))
        assertEquals(listOf(seedDraft.id), syncScheduler.scheduledDraftIds)

        assertRouteClearedFromBackStack<CheckYourAnswersRoute>(navController)
    }

    @Test
    fun tappingAChangeLinkWithNoGearUseEditContextNavigatesViaOnNavigate() {
        // The "Vessel" row has no `changeGearUseId`, so `CheckYourAnswersScreen`'s own `onChangeRow` lambda
        // must route it through `onNavigate(row.changeStep)`, not `onNavigateToEdit` — exercising the real
        // branch inside `CheckYourAnswersScreen` itself (not the pure `CheckYourAnswersScreenContent`).
        val seedDraft = completeDraft()
        val draftRepository = FakeDraftRepository(seedDraft)
        val viewModel =
            buildViewModel(
                draftRepository = draftRepository,
                submissionRepository = FakeSubmissionRepository(succeeds = true),
                connectivityChecker = FakeConnectivityChecker(connected = true),
                syncScheduler = RecordingSyncScheduler(),
            )
        var editObserved: CheckYourAnswersRow? = null
        val navController = launchHarnessAndReachCheckYourAnswers(viewModel) { editObserved = it }

        composeTestRule
            .onNodeWithTag("${CheckYourAnswersScreenTestTags.CHANGE_ACTION_PREFIX}_${CheckYourAnswersFieldKind.Vessel}")
            .performScrollTo()
            .performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            navController.currentDestination?.hasRoute<VesselSelectionRoute>() == true
        }
        composeTestRule.waitForIdle()

        // onNavigate (not onNavigateToEdit) drove this: the destination is the plain VesselSelection route,
        // and the edit-context callback was never invoked.
        assertEquals(null, editObserved)
    }

    @Test
    fun tappingAChangeLinkWithAGearUseEditContextNavigatesViaOnNavigateToEdit() {
        // The confirmed species row carries `changeGearUseId = "gear-use-1"`, so `CheckYourAnswersScreen`'s
        // `onChangeRow` lambda must route it through `onNavigateToEdit(row.changeStep, gearUseId)`.
        val seedDraft = completeDraft()
        val draftRepository = FakeDraftRepository(seedDraft)
        val viewModel =
            buildViewModel(
                draftRepository = draftRepository,
                submissionRepository = FakeSubmissionRepository(succeeds = true),
                connectivityChecker = FakeConnectivityChecker(connected = true),
                syncScheduler = RecordingSyncScheduler(),
            )
        var editObserved: CheckYourAnswersRow? = null
        val navController = launchHarnessAndReachCheckYourAnswers(viewModel) { editObserved = it }

        composeTestRule
            .onNodeWithTag(
                "${CheckYourAnswersScreenTestTags.CHANGE_ACTION_PREFIX}_${CheckYourAnswersFieldKind.Species}",
            ).performScrollTo()
            .performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            navController.currentDestination?.hasRoute<GearSpeciesChecklistRoute>() == true
        }
        composeTestRule.waitForIdle()

        assertEquals(WizardStep.GearSpeciesChecklist, editObserved?.changeStep)
        assertEquals("gear-use-1", editObserved?.changeGearUseId)
    }

    @Test
    fun onlineSubmissionAttemptThatFailsFallsBackToPendingSyncPathAndStillEnqueuesSync() {
        val seedDraft = completeDraft()
        val draftRepository = FakeDraftRepository(seedDraft)
        val submissionRepository = FakeSubmissionRepository(succeeds = false)
        val syncScheduler = RecordingSyncScheduler()
        val viewModel =
            buildViewModel(
                draftRepository = draftRepository,
                submissionRepository = submissionRepository,
                connectivityChecker = FakeConnectivityChecker(connected = true),
                syncScheduler = syncScheduler,
            )

        val navController = launchHarnessAndReachCheckYourAnswers(viewModel)

        composeTestRule
            .onNodeWithTag(CheckYourAnswersScreenTestTags.SUBMIT_ACTION)
            .performScrollTo()
            .performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            navController.currentDestination?.hasRoute<SubmissionPendingSyncRoute>() == true
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(SubmissionPendingSyncScreenTestTags.SCREEN).assertIsDisplayed()
        assertEquals(1, submissionRepository.submitCallCount)
        assertEquals(DraftStatus.PendingSync, draftRepository.statusOf(seedDraft.id))
        assertEquals(listOf(seedDraft.id), syncScheduler.scheduledDraftIds)
    }
}
