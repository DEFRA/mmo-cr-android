@file:Suppress("detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.common.navigation.Destination
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

/**
 * Exercises the **real production navigation wiring** for Phase 8's "Accept and submit trip details"
 * action, end-to-end: a real [CatchRecordFlowViewModel] (directly constructed — it's `@Inject`-annotated
 * but still a plain class, so no Hilt test infrastructure is required), the real
 * [CheckYourAnswersScreen]/[SubmissionSuccessScreen]/[SubmissionPendingSyncScreen] composables (never a
 * `*ScreenContent` composable in isolation), and a real [androidx.navigation.NavHostController] running the
 * exact same [applySubmissionResultNavOptions] popUpTo logic `MmoNavHost` uses (imported from `WizardStep`,
 * not duplicated) — so this test and production cannot silently drift apart.
 *
 * This exists because the equivalent, pre-existing `CheckYourAnswersScreenTest` only ever rendered
 * `CheckYourAnswersScreenContent` (the pure, viewModel-free renderer) and therefore could not have caught
 * the real defect this test was written to guard against: tapping "Accept and submit trip details" used to
 * update `CatchRecordFlowViewModel` state but never actually navigate anywhere, because `CheckYourAnswersScreen`
 * had no effect observing the ViewModel's asynchronously-resolved `currentStep` (see the `LaunchedEffect` now
 * in `CheckYourAnswersScreen.kt`). No test in this codebase exercised a real `NavHostController` together
 * with a real `CatchRecordFlowViewModel` before this file.
 */
class SubmissionFlowNavigationTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    // --- Fakes (androidTest-local — the `test` source set's fakes of the same name are not visible here) --

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

    // --- Fixtures --------------------------------------------------------------------------------------

    /**
     * A fully-complete draft that [nextWizardStepForDraft] resolves directly to [WizardStep.CheckYourAnswers]
     * (`lateSubmissionWarningAcknowledged = true` deliberately skips the separate, already-covered
     * [WizardStep.LateSubmissionWarning] screen so this test focuses purely on the submit action itself).
     * IDs match [StubReferenceDataRepository]'s real confirmed rows so `CheckYourAnswersSupport.buildSections`
     * resolves real display names rather than falling back to "unknown" placeholders.
     */
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

    /**
     * Mirrors the shape of `MmoNavHost`'s nested `catch_record_flow` graph for exactly the three Phase 8
     * routes under test — same route constants, same [applySubmissionResultNavOptions] call — so a pass
     * here is real evidence about production behaviour, not an artefact of a bespoke test harness.
     */
    @Composable
    private fun SubmissionFlowHarness(
        viewModel: CatchRecordFlowViewModel,
        navController: NavHostController,
    ) {
        MmoTheme {
            NavHost(navController = navController, startDestination = Destination.CatchRecordFlow.GRAPH_ROUTE) {
                navigation(
                    startDestination = Destination.CatchRecordFlow.CHECK_YOUR_ANSWERS_ROUTE,
                    route = Destination.CatchRecordFlow.GRAPH_ROUTE,
                ) {
                    composable(Destination.CatchRecordFlow.CHECK_YOUR_ANSWERS_ROUTE) {
                        CheckYourAnswersScreen(
                            viewModel = viewModel,
                            onNavigate = { step ->
                                navController.navigate(routeFor(step)) { applySubmissionResultNavOptions(step) }
                            },
                            onBack = {},
                        )
                    }
                    composable(Destination.CatchRecordFlow.SUBMISSION_SUCCESS_ROUTE) {
                        SubmissionSuccessScreen(viewModel = viewModel, onViewRecords = {}, onBack = {})
                    }
                    composable(Destination.CatchRecordFlow.SUBMISSION_PENDING_SYNC_ROUTE) {
                        SubmissionPendingSyncScreen(viewModel = viewModel, onViewRecords = {}, onBack = {})
                    }
                }
            }
        }
    }

    /** Asserts [route] is no longer reachable on [navController]'s back stack (throws if still present). */
    private fun assertRouteClearedFromBackStack(
        navController: NavHostController,
        route: String,
    ) {
        val stillOnBackStack = runCatching { navController.getBackStackEntry(route) }.isSuccess
        assertTrue(
            "Expected route '$route' to have been cleared from the back stack by applySubmissionResultNavOptions",
            !stillOnBackStack,
        )
    }

    private fun launchHarnessAndReachCheckYourAnswers(viewModel: CatchRecordFlowViewModel): NavHostController {
        lateinit var navController: NavHostController
        composeTestRule.setContent {
            navController = rememberNavController()
            SubmissionFlowHarness(viewModel = viewModel, navController = navController)
        }

        // `EnterFlow` is genuinely asynchronous (loads reference data + the existing draft) — wait for the
        // real ViewModel to resolve it, exactly as `CatchRecordFlowEntryScreen` does in production.
        viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
        composeTestRule.waitUntil(timeoutMillis = 5_000) { viewModel.state.value.status is UiStatus.Content<*> }
        composeTestRule.waitForIdle()
        return navController
    }

    // --- Tests -----------------------------------------------------------------------------------------

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
            navController.currentDestination?.route == Destination.CatchRecordFlow.SUBMISSION_SUCCESS_ROUTE
        }
        composeTestRule.waitForIdle()

        // Real navigation to the real screen, not just a state change.
        composeTestRule.onNodeWithTag(SubmissionSuccessScreenTestTags.SCREEN).assertIsDisplayed()
        composeTestRule.onNodeWithText("Your catch record reference A1234520260727150815").assertIsDisplayed()

        // Real submission call chain reached (not skipped) and the draft's persisted status flipped.
        assertEquals(1, submissionRepository.submitCallCount)
        assertEquals(DraftStatus.Submitted, draftRepository.statusOf(seedDraft.id))
        assertTrue("Online success must not enqueue a background sync", syncScheduler.scheduledDraftIds.isEmpty())

        // Back stack genuinely cleared: the user cannot navigate back into an already-submitted draft.
        assertRouteClearedFromBackStack(navController, Destination.CatchRecordFlow.CHECK_YOUR_ANSWERS_ROUTE)
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
            navController.currentDestination?.route == Destination.CatchRecordFlow.SUBMISSION_PENDING_SYNC_ROUTE
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(SubmissionPendingSyncScreenTestTags.SCREEN).assertIsDisplayed()
        composeTestRule.onNodeWithText("Your catch record reference A1234520260727150815").assertIsDisplayed()

        // Offline path never attempts the network submission call.
        assertEquals(0, submissionRepository.submitCallCount)
        assertEquals(DraftStatus.PendingSync, draftRepository.statusOf(seedDraft.id))
        // The real call site that would enqueue `CatchRecordSyncWorker` via WorkManager in production
        // (`WorkManagerCatchRecordSyncScheduler.scheduleSync`) is genuinely reached, for this exact draft id.
        assertEquals(listOf(seedDraft.id), syncScheduler.scheduledDraftIds)

        assertRouteClearedFromBackStack(navController, Destination.CatchRecordFlow.CHECK_YOUR_ANSWERS_ROUTE)
    }

    @Test
    fun onlineSubmissionAttemptThatFailsFallsBackToPendingSyncPathAndStillEnqueuesSync() {
        // `acceptDeclarationAndSubmit`'s documented fallback: connected but the online attempt itself fails.
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
            navController.currentDestination?.route == Destination.CatchRecordFlow.SUBMISSION_PENDING_SYNC_ROUTE
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(SubmissionPendingSyncScreenTestTags.SCREEN).assertIsDisplayed()
        assertEquals(1, submissionRepository.submitCallCount)
        assertEquals(DraftStatus.PendingSync, draftRepository.statusOf(seedDraft.id))
        assertEquals(listOf(seedDraft.id), syncScheduler.scheduledDraftIds)
    }
}
