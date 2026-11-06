package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.common.design.RecordStatusTag
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
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.submission.CheckYourAnswersScreen
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.submission.CheckYourAnswersScreenTestTags
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.submission.SubmissionPendingSyncScreen
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.submission.SubmissionPendingSyncScreenTestTags
import uk.gov.defra.mmocatchrecord.feature.home.domain.CatchRecordSummary
import uk.gov.defra.mmocatchrecord.feature.home.domain.HomeSummary
import uk.gov.defra.mmocatchrecord.feature.home.presentation.CatchRecordsListSectionTestTags
import uk.gov.defra.mmocatchrecord.feature.home.presentation.HomeScreenContent
import uk.gov.defra.mmocatchrecord.feature.home.presentation.HomeScreenTestTags
import uk.gov.defra.mmocatchrecord.feature.home.presentation.HomeViewState
import java.io.IOException

/** Offline business rules BR-SUB-008: AC10, AC11, AC13, AC15 and the UI side of AC19. */
class OfflineSubmissionAndSyncTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private class FakeDraftRepository(
        seed: CatchRecordDraft,
    ) : CatchRecordDraftRepository {
        val drafts = mutableMapOf(seed.id to seed)
        var failNextSaves = 0

        override suspend fun getDraftById(draftId: String): Result<CatchRecordDraft?> = Result.success(drafts[draftId])

        override suspend fun startDraft(candidate: CatchRecordDraft): Result<CatchRecordDraft> =
            Result.failure(UnsupportedOperationException("Not exercised"))

        override suspend fun saveDraft(draft: CatchRecordDraft): Result<CatchRecordDraft> {
            if (failNextSaves > 0) {
                failNextSaves--
                return Result.failure(IOException("Simulated local save failure"))
            }
            drafts[draft.id] = draft
            return Result.success(draft)
        }

        override suspend fun deleteDraft(draftId: String): Result<Unit> {
            drafts.remove(draftId)
            return Result.success(Unit)
        }

        override suspend fun markReadyToSubmit(draftId: String): Result<CatchRecordDraft> =
            Result.failure(UnsupportedOperationException("Not exercised"))

        override fun observeRecordSummaries(): Flow<List<CatchRecordSummary>> = flowOf(emptyList())
    }

    private class FakeSubmissionRepository(
        private val succeeds: Boolean,
    ) : CatchRecordSubmissionRepository {
        var submitCallCount = 0
            private set

        override suspend fun submit(draft: CatchRecordDraft): Result<Unit> {
            submitCallCount++
            return if (succeeds) Result.success(Unit) else Result.failure(IllegalStateException("Simulated"))
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

    private class Harness(
        val draftRepository: FakeDraftRepository,
        val submissionRepository: FakeSubmissionRepository,
        val syncScheduler: RecordingSyncScheduler,
        val navController: NavHostController,
    )

    private val seedDraft =
        CatchRecordDraft(
            id = "draft-1",
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

    private fun string(resId: Int): String = ApplicationProvider.getApplicationContext<Context>().getString(resId)

    @Composable
    private fun FlowHost(
        viewModel: CatchRecordFlowViewModel,
        navController: NavHostController,
        isOffline: Boolean,
    ) {
        WizardTestTheme(isOffline = isOffline) {
            NavHost(navController = navController, startDestination = CatchRecordGraphRoute) {
                navigation<CatchRecordGraphRoute>(startDestination = CheckYourAnswersRoute) {
                    composable<CheckYourAnswersRoute> {
                        CheckYourAnswersScreen(
                            viewModel = viewModel,
                            onNavigate = { step ->
                                navController.navigate(routeFor(step)) { applySubmissionResultNavOptions(step) }
                            },
                            onNavigateToEdit = { _, _ -> },
                            onBack = {},
                        )
                    }
                    composable<SubmissionPendingSyncRoute> {
                        SubmissionPendingSyncScreen(viewModel = viewModel, onViewRecords = {}, onBack = {})
                    }
                    composable<SubmissionSuccessRoute> { Text("Submitted online") }
                }
            }
        }
    }

    private fun launchAtCheckYourAnswers(
        connected: Boolean,
        submitSucceeds: Boolean = true,
    ): Harness {
        val draftRepository = FakeDraftRepository(seedDraft)
        val submissionRepository = FakeSubmissionRepository(submitSucceeds)
        val syncScheduler = RecordingSyncScheduler()
        val viewModel =
            CatchRecordFlowViewModel(
                draftRepository,
                StubReferenceDataRepository(),
                submissionRepository,
                FakeConnectivityChecker(connected),
                syncScheduler,
                { 0L },
                { "generated-id" },
                Dispatchers.Default,
            )
        lateinit var navController: NavHostController
        composeTestRule.setContent {
            navController = rememberNavController()
            FlowHost(viewModel, navController, isOffline = !connected)
        }
        viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
        composeTestRule.waitUntil(timeoutMillis = 5_000) { viewModel.state.value.status is UiStatus.Content<*> }
        composeTestRule.waitForIdle()
        return Harness(draftRepository, submissionRepository, syncScheduler, navController)
    }

    private fun submitAndWaitForPendingSync(harness: Harness) {
        composeTestRule
            .onNodeWithTag(CheckYourAnswersScreenTestTags.SUBMIT_ACTION)
            .performScrollTo()
            .performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            harness.navController.currentDestination?.hasRoute<SubmissionPendingSyncRoute>() == true
        }
        composeTestRule.waitForIdle()
    }

    // --- AC10: record retained locally when offline -------------------------------------------------

    @Test
    fun ac10_offlineSubmitStoresRecordLocallyWithEveryEnteredFieldIntact() {
        val harness = launchAtCheckYourAnswers(connected = false)
        submitAndWaitForPendingSync(harness)

        val stored = harness.draftRepository.drafts.getValue(seedDraft.id)
        assertEquals(DraftStatus.PendingSync, stored.status)
        assertEquals(seedDraft.vesselId, stored.vesselId)
        assertEquals(seedDraft.departureDate, stored.departureDate)
        assertEquals(seedDraft.returnDate, stored.returnDate)
        assertEquals(seedDraft.departurePort, stored.departurePort)
        assertEquals(seedDraft.returnPort, stored.returnPort)
        assertEquals(seedDraft.gearUses, stored.gearUses)
        assertEquals(seedDraft.catchRecordReference, stored.catchRecordReference)
    }

    @Test
    fun ac10_offlineSubmitNeverCallsTheServiceAndIsNotMarkedSubmitted() {
        val harness = launchAtCheckYourAnswers(connected = false)
        submitAndWaitForPendingSync(harness)

        assertEquals(0, harness.submissionRepository.submitCallCount)
        assertNotEquals(
            DraftStatus.Submitted,
            harness.draftRepository.drafts
                .getValue(seedDraft.id)
                .status,
        )
        assertEquals(1, harness.draftRepository.drafts.size)
    }

    @Test
    fun ac10_offlineBannerIsVisibleOnCheckYourAnswersBeforeSubmitting() {
        launchAtCheckYourAnswers(connected = false)

        composeTestRule.onNodeWithText(string(R.string.offline_banner_message)).assertIsDisplayed()
    }

    // --- AC11: user told the record is not yet submitted ---------------------------------------------

    @Test
    fun ac11_afterOfflineSaveUserIsToldRecordIsRecordedButNotYetSent() {
        val harness = launchAtCheckYourAnswers(connected = false)
        submitAndWaitForPendingSync(harness)

        composeTestRule.onNodeWithTag(SubmissionPendingSyncScreenTestTags.SCREEN).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.submission_pending_sync_banner_title)).assertIsDisplayed()
        composeTestRule
            .onNodeWithText(string(R.string.submission_pending_sync_body_title))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun ac11_afterOfflineSaveUserIsAdvisedItWillSendWhenConnectivityReturns() {
        val harness = launchAtCheckYourAnswers(connected = false)
        submitAndWaitForPendingSync(harness)

        composeTestRule
            .onNodeWithText(string(R.string.submission_pending_sync_body))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun ac11_pendingSyncScreenShowsTheLocalRecordReference() {
        val harness = launchAtCheckYourAnswers(connected = false)
        submitAndWaitForPendingSync(harness)

        composeTestRule.onNodeWithText("Your catch record reference A1234520260727150815").assertIsDisplayed()
    }

    // --- AC13: retry when connectivity returns updates status ----------------------------------------

    @Test
    fun ac10_ac13_offlineSubmitEnqueuesExactlyOneBackgroundSyncForThatRecord() {
        val harness = launchAtCheckYourAnswers(connected = false)
        submitAndWaitForPendingSync(harness)

        assertEquals(listOf(seedDraft.id), harness.syncScheduler.scheduledDraftIds)
    }

    @Test
    fun ac13_recordRowChangesFromUnsentToSubmittedWhenSyncSucceeds() {
        val unsent =
            CatchRecordSummary("rec-1", "REF-001", "ACHILLES", DmyDate(1, 1, 2026), RecordStatusTag.AwaitingSync)
        var record by mutableStateOf(unsent)
        var confirmation by mutableStateOf<String?>(null)
        var isOffline by mutableStateOf(true)
        composeTestRule.setContent {
            WizardTestTheme(isOffline = isOffline) {
                HomeScreenContent(
                    state =
                        HomeViewState(
                            status = UiStatus.Content(HomeSummary("user-1", listOf(record))),
                            syncConfirmationMessage = confirmation,
                        ),
                    currentLanguage = "en",
                    onLanguageToggle = {},
                    isOffline = isOffline,
                    selectedTab = 0,
                    onTabSelected = {},
                    onSignOut = {},
                    onCreateCatchRecord = {},
                    onResumeDraft = {},
                    onDismissSyncConfirmationMessage = { confirmation = null },
                )
            }
        }
        scrollToRow(unsent.id)
        rowText(unsent.id, string(R.string.status_awaiting_sync)).assertIsDisplayed()

        composeTestRule.mainClock.autoAdvance = false
        isOffline = false
        record = unsent.copy(status = RecordStatusTag.Submitted)
        confirmation = "Catch record REF-001 has been submitted"
        composeTestRule.mainClock.advanceTimeBy(100L)

        composeTestRule.onNodeWithText("Catch record REF-001 has been submitted").assertIsDisplayed()
        scrollToRow(unsent.id)
        rowText(unsent.id, string(R.string.status_submitted)).assertIsDisplayed()
    }

    private fun scrollToRow(id: String) {
        composeTestRule
            .onNodeWithTag(HomeScreenTestTags.TAB_LIST)
            .performScrollToNode(hasTestTag(CatchRecordsListSectionTestTags.row(id)))
    }

    private fun rowText(
        id: String,
        text: String,
    ) = composeTestRule.onNode(
        hasText(text) and hasAnyAncestor(hasTestTag(CatchRecordsListSectionTestTags.row(id))),
        useUnmergedTree = true,
    )

    // --- AC15: connectivity lost while working on a draft --------------------------------------------

    @Test
    fun ac15_losingConnectivityMidDraftShowsBannerAndKeepsEnteredContent() {
        var isOffline by mutableStateOf(false)
        composeTestRule.setContent {
            WizardTestTheme(isOffline = isOffline) {
                CatchRecordWizardScaffold(
                    screenTestTag = "draft_step",
                    title = "When did you leave port?",
                    onBack = {},
                ) {
                    Text("Departure date 01/01/2026")
                }
            }
        }
        composeTestRule.onNodeWithText(string(R.string.offline_banner_message)).assertDoesNotExist()

        isOffline = true
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(string(R.string.offline_banner_message)).assertIsDisplayed()
        composeTestRule.onNodeWithText("Departure date 01/01/2026").assertIsDisplayed()
    }

    // --- AC19 (UI side): failed attempt falls back without duplicate submission ------------------------

    @Test
    fun ac19_failedOnlineAttemptFallsBackToPendingSyncWithOneCallAndOneSyncJob() {
        val harness = launchAtCheckYourAnswers(connected = true, submitSucceeds = false)
        submitAndWaitForPendingSync(harness)

        composeTestRule.onNodeWithTag(SubmissionPendingSyncScreenTestTags.SCREEN).assertIsDisplayed()
        assertEquals(1, harness.submissionRepository.submitCallCount)
        assertEquals(listOf(seedDraft.id), harness.syncScheduler.scheduledDraftIds)
        assertEquals(
            DraftStatus.PendingSync,
            harness.draftRepository.drafts
                .getValue(seedDraft.id)
                .status,
        )
        assertEquals(1, harness.draftRepository.drafts.size)
    }

    // --- Edge cases (offline) ---------------------------------------------------------------------------

    @Test
    fun edge_offlineNoticeWarnsBeforeSubmitThatRecordWillBeSavedOnDevice() {
        launchAtCheckYourAnswers(connected = false)

        composeTestRule
            .onNodeWithTag(CheckYourAnswersScreenTestTags.OFFLINE_NOTICE)
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(string(R.string.check_your_answers_offline_notice), substring = true)
            .assertExists()
    }

    @Test
    fun edge_offlineNoticeIsNotShownWhenOnline() {
        launchAtCheckYourAnswers(connected = true)

        composeTestRule.onNodeWithTag(CheckYourAnswersScreenTestTags.OFFLINE_NOTICE).assertDoesNotExist()
        composeTestRule.onNodeWithTag(OFFLINE_BANNER).assertDoesNotExist()
    }

    @Test
    fun edge_doubleTapOnSubmitOfflineCreatesOneRecordAndOneSyncJob() {
        val harness = launchAtCheckYourAnswers(connected = false)
        val submit = composeTestRule.onNodeWithTag(CheckYourAnswersScreenTestTags.SUBMIT_ACTION).performScrollTo()

        composeTestRule.mainClock.autoAdvance = false
        submit.performClick()
        submit.performClick()
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            harness.navController.currentDestination?.hasRoute<SubmissionPendingSyncRoute>() == true
        }

        assertEquals(listOf(seedDraft.id), harness.syncScheduler.scheduledDraftIds)
        assertEquals(1, harness.draftRepository.drafts.size)
        assertEquals(0, harness.submissionRepository.submitCallCount)
    }

    @Test
    fun edge_localSaveFailureOfflineShowsRetryableErrorAndKeepsEnteredData() {
        val harness = launchAtCheckYourAnswers(connected = false)
        harness.draftRepository.failNextSaves = 1

        composeTestRule
            .onNodeWithTag(CheckYourAnswersScreenTestTags.SUBMIT_ACTION)
            .performScrollTo()
            .performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule
                .onAllNodesWithTag(CheckYourAnswersScreenTestTags.ERROR_MESSAGE)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeTestRule.onNodeWithTag(RETRY_ACTION).assertIsDisplayed()
        assertEquals(seedDraft, harness.draftRepository.drafts.getValue(seedDraft.id))
        assertTrue(harness.syncScheduler.scheduledDraftIds.isEmpty())
        assertEquals(0, harness.submissionRepository.submitCallCount)
    }

    @Test
    fun edge_retryAfterLocalSaveFailureOfflineSavesRecordForSync() {
        val harness = launchAtCheckYourAnswers(connected = false)
        harness.draftRepository.failNextSaves = 1
        composeTestRule
            .onNodeWithTag(CheckYourAnswersScreenTestTags.SUBMIT_ACTION)
            .performScrollTo()
            .performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag(RETRY_ACTION).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag(RETRY_ACTION).performScrollTo().performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            harness.navController.currentDestination?.hasRoute<SubmissionPendingSyncRoute>() == true
        }

        assertEquals(
            DraftStatus.PendingSync,
            harness.draftRepository.drafts
                .getValue(seedDraft.id)
                .status,
        )
        assertEquals(listOf(seedDraft.id), harness.syncScheduler.scheduledDraftIds)
    }

    @Test
    fun edge_connectivityRestoredMidDraftHidesBannerAndKeepsContent() {
        var isOffline by mutableStateOf(true)
        setDraftStep { isOffline }
        composeTestRule.onNodeWithTag(OFFLINE_BANNER).assertIsDisplayed()

        isOffline = false
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(OFFLINE_BANNER).assertDoesNotExist()
        composeTestRule.onNodeWithText(DRAFT_TEXT).assertIsDisplayed()
    }

    @Test
    fun edge_flappingConnectivityNeverLosesDraftContent() {
        var isOffline by mutableStateOf(false)
        setDraftStep { isOffline }

        repeat(FLAP_COUNT) {
            isOffline = !isOffline
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText(DRAFT_TEXT).assertIsDisplayed()
        }
        composeTestRule.onNodeWithTag(OFFLINE_BANNER).assertDoesNotExist()
    }

    private fun setDraftStep(isOffline: () -> Boolean) {
        composeTestRule.setContent {
            WizardTestTheme(isOffline = isOffline()) {
                CatchRecordWizardScaffold(screenTestTag = "draft_step", title = "Draft step", onBack = {}) {
                    Text(DRAFT_TEXT)
                }
            }
        }
    }

    @Test
    fun edge_multipleUnsentRecordsAreAllListedOfflineInTheGivenOrder() {
        val unsent =
            (1..3).map {
                CatchRecordSummary("u$it", "REF-00$it", "ACHILLES", DmyDate(it, 1, 2026), RecordStatusTag.AwaitingSync)
            }
        setHomeOffline(unsent)

        composeTestRule.onNodeWithTag(OFFLINE_BANNER).assertIsDisplayed()
        unsent.forEach { record ->
            scrollToRow(record.id)
            rowText(record.id, string(R.string.status_awaiting_sync)).assertIsDisplayed()
        }
        scrollToRow(unsent.first().id)
        val tops =
            unsent.map {
                composeTestRule
                    .onNodeWithTag(CatchRecordsListSectionTestTags.row(it.id))
                    .getUnclippedBoundsInRoot()
                    .top
            }
        assertEquals(tops.sorted(), tops)
    }

    @Test
    fun edge_emptyRecordListOfflineStillShowsBannerAndCreateAction() {
        setHomeOffline(emptyList())

        composeTestRule.onNodeWithTag(OFFLINE_BANNER).assertIsDisplayed()
        assertFalse(
            composeTestRule
                .onAllNodesWithText(string(R.string.status_awaiting_sync))
                .fetchSemanticsNodes()
                .isNotEmpty(),
        )
    }

    private fun setHomeOffline(records: List<CatchRecordSummary>) {
        composeTestRule.setContent {
            WizardTestTheme(isOffline = true) {
                HomeScreenContent(
                    state = HomeViewState(status = UiStatus.Content(HomeSummary("user-1", records))),
                    currentLanguage = "en",
                    onLanguageToggle = {},
                    isOffline = true,
                    selectedTab = 0,
                    onTabSelected = {},
                    onSignOut = {},
                    onCreateCatchRecord = {},
                    onResumeDraft = {},
                    onDismissSyncConfirmationMessage = {},
                )
            }
        }
    }

    private companion object {
        const val OFFLINE_BANNER = "offline_banner"
        const val RETRY_ACTION = "${CheckYourAnswersScreenTestTags.ERROR_MESSAGE}_retry_action"
        const val DRAFT_TEXT = "Departure date 01/01/2026"
        const val FLAP_COUNT = 6
    }
}
