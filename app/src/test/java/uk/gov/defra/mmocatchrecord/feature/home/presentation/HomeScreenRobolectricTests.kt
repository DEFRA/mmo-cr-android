package uk.gov.defra.mmocatchrecord.feature.home.presentation

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uk.gov.defra.mmocatchrecord.common.design.DebugSettingsSection
import uk.gov.defra.mmocatchrecord.common.design.GdsTopAppBarTestTags
import uk.gov.defra.mmocatchrecord.common.design.MmoBottomNavigationBarTestTags
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.common.design.RecordStatusTag
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.core.connectivity.ConnectivityViewModel
import uk.gov.defra.mmocatchrecord.core.connectivity.FakeConnectivityObserver
import uk.gov.defra.mmocatchrecord.core.language.AppLanguage
import uk.gov.defra.mmocatchrecord.core.language.AppLanguageViewModel
import uk.gov.defra.mmocatchrecord.core.language.FakeAppLanguageRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DmyDate
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.RetryCatchRecordSubmissionUseCase
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeCatchRecordSyncScheduler
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeNetworkConnectivityChecker
import uk.gov.defra.mmocatchrecord.feature.home.data.FakeHomeRepository
import uk.gov.defra.mmocatchrecord.feature.home.domain.CatchRecordSummary
import uk.gov.defra.mmocatchrecord.feature.home.domain.HomeSummary
import uk.gov.defra.mmocatchrecord.feature.home.domain.ObserveHomeSummaryUseCase

/**
 * JVM/Robolectric Compose coverage for the Home records list and offline banner — required because
 * `connectedDebugAndroidTest` coverage is not merged into Kover/SonarCloud (see ADR 0014 Phase B).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HomeScreenRobolectricTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val everyStatusRecords =
        listOf(
            CatchRecordSummary("1", "MMO-REF-001", "ACHILLES", DmyDate(1, 1, 2026), RecordStatusTag.Submitted),
            CatchRecordSummary("2", "MMO-REF-002", "ACHILLES", DmyDate(2, 1, 2026), RecordStatusTag.ReadyToSubmit),
            CatchRecordSummary("3", null, "ACHILLES", null, RecordStatusTag.Draft),
            CatchRecordSummary("4", "MMO-REF-004", "ACHILLES", DmyDate(4, 1, 2026), RecordStatusTag.AwaitingSync),
        )

    private val statusLabelById =
        mapOf("1" to "Submitted", "2" to "Ready to submit", "3" to "Draft", "4" to "Waiting to send")

    private fun setContent(
        records: List<CatchRecordSummary> = everyStatusRecords,
        isOffline: Boolean = false,
        syncConfirmationMessage: String? = null,
    ) {
        val state =
            HomeViewState(
                status = UiStatus.Content(HomeSummary("alice", records)),
                syncConfirmationMessage = syncConfirmationMessage,
            )
        composeTestRule.setContent {
            MmoTheme {
                HomeScreenContent(
                    state = state,
                    currentLanguage = "en",
                    onLanguageToggle = {},
                    isOffline = isOffline,
                    selectedTab = 0,
                    onTabSelected = {},
                    onSignOut = {},
                    onCreateCatchRecord = {},
                    onResumeDraft = {},
                )
            }
        }
    }

    /** Builds a real [HomeViewModel] backed by fakes — needed to exercise the stateful [HomeScreen] wrapper
     * (the Hilt-resolving overload), not just the stateless [HomeScreenContent] already covered above. */
    private fun buildHomeViewModel(
        repository: FakeHomeRepository,
        dispatcher: CoroutineDispatcher,
        context: Context = mock(),
        syncScheduler: FakeCatchRecordSyncScheduler = FakeCatchRecordSyncScheduler(),
        connectivityChecker: FakeNetworkConnectivityChecker = FakeNetworkConnectivityChecker(),
    ): HomeViewModel =
        HomeViewModel(
            ObserveHomeSummaryUseCase(repository),
            RetryCatchRecordSubmissionUseCase(syncScheduler, connectivityChecker),
            DebugSettingsSection {},
            context,
            dispatcher,
        )

    /** The records list is a `LazyColumn` (R14) — off-screen rows must be scrolled into view to compose. */
    private fun scrollToRow(id: String) {
        composeTestRule
            .onNodeWithTag(HomeScreenTestTags.TAB_LIST)
            .performScrollToNode(hasTestTag(CatchRecordsListSectionTestTags.row(id)))
    }

    @Test
    fun `records list renders a row for every one of the four statuses`() {
        setContent()

        everyStatusRecords.forEach { record ->
            scrollToRow(record.id)
            composeTestRule.onNodeWithTag(CatchRecordsListSectionTestTags.row(record.id)).assertIsDisplayed()
            composeTestRule.onNodeWithText(statusLabelById.getValue(record.id)).assertIsDisplayed()
        }
    }

    @Test
    fun `retry button is shown only for the awaiting-sync row`() {
        setContent()

        listOf("1", "2", "3").forEach { id ->
            scrollToRow(id)
            composeTestRule.onNodeWithTag(CatchRecordsListSectionTestTags.row(id)).assertIsDisplayed()
            composeTestRule.onNodeWithTag(CatchRecordsListSectionTestTags.retry(id)).assertDoesNotExist()
        }
        scrollToRow("4")
        composeTestRule.onNodeWithTag(CatchRecordsListSectionTestTags.retry("4")).assertIsDisplayed()
    }

    /** ADR-0014 Phase F: the table must fit a typical ~412dp-wide phone without a manual horizontal
     * scroll, matching the reference design — only narrower/zoomed viewports fall back to scrolling. */
    @Config(qualifiers = "w412dp-h915dp")
    @Test
    fun `created by column is visible without horizontal scrolling at a typical phone width`() {
        setContent()

        scrollToRow("1")
        composeTestRule.onNodeWithText("Created by").assertIsDisplayed()
        composeTestRule.onNodeWithTag(CatchRecordsListSectionTestTags.createdBy("1")).assertIsDisplayed()
        composeTestRule.onNodeWithTag(CatchRecordsListSectionTestTags.createdBy("1")).assertTextEquals("You")
    }

    @Test
    fun `offline banner is shown when offline and absent when online`() {
        setContent(isOffline = true)
        composeTestRule.onNodeWithTag("offline_banner").assertIsDisplayed()
    }

    @Test
    fun `offline banner is absent when connectivity is available`() {
        setContent(isOffline = false)
        composeTestRule.onNodeWithTag("offline_banner").assertDoesNotExist()
    }

    @Test
    fun `FR9 sync confirmation message is shown when a record has just been submitted`() {
        setContent(syncConfirmationMessage = "Catch record MMO-REF-004 has been submitted")
        composeTestRule.onNodeWithTag(SyncConfirmationMessageTestTags.MESSAGE).assertIsDisplayed()
        composeTestRule.onNodeWithText("Catch record MMO-REF-004 has been submitted").assertIsDisplayed()
    }

    @Test
    fun `FR9 sync confirmation message is absent when there is nothing to confirm`() {
        setContent(syncConfirmationMessage = null)
        composeTestRule.onNodeWithTag(SyncConfirmationMessageTestTags.MESSAGE).assertDoesNotExist()
    }

    /** Exercises the composable directly for coverage — @Preview functions are never invoked at runtime
     * otherwise, so `HomeScreenPreview` would stay uncounted new code (ADR-0014 Phase F rebuild). */
    @Test
    fun `home screen preview composable renders without crashing`() {
        composeTestRule.setContent { HomeScreenPreview() }
        composeTestRule.onNodeWithTag(HomeScreenTestTags.SCREEN).assertIsDisplayed()
    }

    @Test
    fun `loading indicator is shown while the summary status is idle or loading`() {
        composeTestRule.setContent {
            MmoTheme {
                HomeScreenContent(
                    state = HomeViewState(status = UiStatus.Loading),
                    currentLanguage = "en",
                    onLanguageToggle = {},
                    isOffline = false,
                    selectedTab = 0,
                    onTabSelected = {},
                    onSignOut = {},
                    onCreateCatchRecord = {},
                    onResumeDraft = {},
                )
            }
        }
        composeTestRule
            .onNodeWithTag(HomeScreenTestTags.TAB_LIST)
            .performScrollToNode(hasTestTag("home_loading_indicator"))
        composeTestRule.onNodeWithTag("home_loading_indicator").assertIsDisplayed()
        composeTestRule.onNodeWithTag(HomeScreenTestTags.ERROR_MESSAGE).assertDoesNotExist()
    }

    @Test
    fun `error status renders the error message`() {
        composeTestRule.setContent {
            MmoTheme {
                HomeScreenContent(
                    state =
                        HomeViewState(status = UiStatus.Error("We could not load records", isRetryable = true)),
                    currentLanguage = "en",
                    onLanguageToggle = {},
                    isOffline = false,
                    selectedTab = 0,
                    onTabSelected = {},
                    onSignOut = {},
                    onCreateCatchRecord = {},
                    onResumeDraft = {},
                )
            }
        }
        composeTestRule
            .onNodeWithTag(HomeScreenTestTags.TAB_LIST)
            .performScrollToNode(hasTestTag(HomeScreenTestTags.ERROR_MESSAGE))
        composeTestRule.onNodeWithTag(HomeScreenTestTags.ERROR_MESSAGE).assertIsDisplayed()
        composeTestRule.onNodeWithText("We could not load records").assertIsDisplayed()
    }

    /** ADR 0014 Phase C: the FR8 "retried while offline" message renders alongside the records list. */
    @Test
    fun `offline retry message is shown when present`() {
        composeTestRule.setContent {
            MmoTheme {
                HomeScreenContent(
                    state =
                        HomeViewState(
                            status = UiStatus.Content(HomeSummary("alice", everyStatusRecords)),
                            offlineRetryMessage = "Catch record retry will resume when you're back online",
                        ),
                    currentLanguage = "en",
                    onLanguageToggle = {},
                    isOffline = false,
                    selectedTab = 0,
                    onTabSelected = {},
                    onSignOut = {},
                    onCreateCatchRecord = {},
                    onResumeDraft = {},
                )
            }
        }
        composeTestRule.onNodeWithTag(OfflineRetryMessageTestTags.MESSAGE).assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Catch record retry will resume when you're back online")
            .assertIsDisplayed()
    }

    @Test
    fun `notifications tab shows the notifications tab content`() {
        composeTestRule.setContent {
            MmoTheme {
                HomeScreenContent(
                    state = HomeViewState(status = UiStatus.Content(HomeSummary("alice", everyStatusRecords))),
                    currentLanguage = "en",
                    onLanguageToggle = {},
                    isOffline = false,
                    selectedTab = 1,
                    onTabSelected = {},
                    onSignOut = {},
                    onCreateCatchRecord = {},
                    onResumeDraft = {},
                )
            }
        }
        composeTestRule.onNodeWithTag(HomeScreenTestTags.NOTIFICATIONS_TAB_HEADING).assertIsDisplayed()
    }

    /** Covers [SettingsTabContent]'s own default [uk.gov.defra.mmocatchrecord.common.design.DebugSettingsSection]
     * seam — the Settings tab is reached via [HomeScreenContent] with its own default, a separate no-op object. */
    @Test
    fun `settings tab shows the settings tab content with the default debug settings section`() {
        composeTestRule.setContent {
            MmoTheme {
                HomeScreenContent(
                    state = HomeViewState(status = UiStatus.Content(HomeSummary("alice", everyStatusRecords))),
                    currentLanguage = "en",
                    onLanguageToggle = {},
                    isOffline = false,
                    selectedTab = 2,
                    onTabSelected = {},
                    onSignOut = {},
                    onCreateCatchRecord = {},
                    onResumeDraft = {},
                )
            }
        }
        composeTestRule.onNodeWithTag(SettingsScreenTestTags.SCREEN).assertIsDisplayed()
    }

    @Test
    fun `settings tab content renders with its own default debug settings section when none is supplied`() {
        composeTestRule.setContent {
            MmoTheme {
                SettingsTabContent(onSignOut = {})
            }
        }
        composeTestRule.onNodeWithTag(SettingsScreenTestTags.SCREEN).assertIsDisplayed()
    }

    @Test
    fun `sign out button and royal crest are reachable by scrolling to the bottom of the list`() {
        var signedOut = false
        composeTestRule.setContent {
            MmoTheme {
                HomeScreenContent(
                    state = HomeViewState(status = UiStatus.Content(HomeSummary("alice", everyStatusRecords))),
                    currentLanguage = "en",
                    onLanguageToggle = {},
                    isOffline = false,
                    selectedTab = 0,
                    onTabSelected = {},
                    onSignOut = { signedOut = true },
                    onCreateCatchRecord = {},
                    onResumeDraft = {},
                )
            }
        }
        composeTestRule
            .onNodeWithTag(HomeScreenTestTags.TAB_LIST)
            .performScrollToNode(hasTestTag(HomeScreenTestTags.ROYAL_CREST))
        composeTestRule.onNodeWithTag(HomeScreenTestTags.ROYAL_CREST).assertIsDisplayed()
        composeTestRule.onNodeWithTag(HomeScreenTestTags.SIGN_OUT_ACTION).performClick()
        assertTrue(signedOut)
    }

    /** [HelpAccordionsSection] is tested standalone (not through the `LazyColumn`) because its body content
     * only composes once expanded (R14 lazy-item virtualisation doesn't apply here). Uses a tall viewport
     * since the section isn't inside a scroll container and both accordions expanded exceed a default window. */
    @Config(qualifiers = "w412dp-h2000dp")
    @Test
    fun `help accordions expand to reveal their body content`() {
        composeTestRule.setContent { MmoTheme { HelpAccordionsSection() } }

        composeTestRule.onNodeWithText("Help with catch recording").performClick()
        composeTestRule.onNodeWithText("What you need to do").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("You need to create your catch record within 24 hours of landing your catch.")
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("Catch record statuses").performClick()
        composeTestRule.onNodeWithText("Draft:").assertIsDisplayed()
        composeTestRule.onNodeWithText("Waiting to send:").assertIsDisplayed()
    }

    /** Covers the 4-column table's header/rows/pagination and [formatDmyDate] via the real preview data,
     * on a tall viewport so every `LazyColumn` item composes without needing an explicit scroll. */
    @Config(qualifiers = "w412dp-h2000dp")
    @Test
    fun `catch records list preview renders the table header, rows and pagination bar`() {
        composeTestRule.setContent { CatchRecordsListSectionPreview() }

        composeTestRule.onNodeWithText("Trip end date").assertIsDisplayed()
        composeTestRule.onNodeWithText("15 Nov 2020").assertIsDisplayed()
        composeTestRule.onNodeWithTag(CatchRecordsListSectionTestTags.PAGINATION_SHOWING).assertIsDisplayed()
    }

    @Test
    fun `catch records list preview shows the empty state for no records`() {
        composeTestRule.setContent { CatchRecordsListSectionEmptyPreview() }

        composeTestRule.onNodeWithTag(CatchRecordsListSectionTestTags.EMPTY_STATE).assertIsDisplayed()
    }

    /** With more than one page of records, both the "Previous" and "Next" pagination links become
     * clickable — otherwise only their disabled branch is ever exercised by the 4-record preview data. */
    @Config(qualifiers = "w412dp-h2000dp")
    @Test
    fun `pagination links are clickable and change page when there is more than one page`() {
        val manyRecords =
            (1..11).map {
                CatchRecordSummary(
                    it.toString(),
                    "MMO-REF-$it",
                    "ACHILLES",
                    DmyDate(it, 1, 2026),
                    RecordStatusTag.Submitted,
                )
            }
        var page by mutableIntStateOf(0)
        composeTestRule.setContent {
            MmoTheme {
                LazyColumn {
                    catchRecordsListSection(
                        records = manyRecords,
                        onRecordClick = {},
                        currentPage = page,
                        onPageChange = { page = it },
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag(CatchRecordsListSectionTestTags.PAGINATION_NEXT).performClick()
        assertTrue(page == 1)
        composeTestRule.onNodeWithTag(CatchRecordsListSectionTestTags.PAGINATION_PREVIOUS).performClick()
        assertTrue(page == 0)
    }

    /** Exercises the remaining content-section `@Preview` functions directly for coverage — they are
     * never invoked at runtime otherwise (ADR-0014 Phase F rebuild). */
    @Test
    fun `important banner and heading preview composables render without crashing`() {
        composeTestRule.setContent {
            Column {
                ImportantBannerSectionPreview()
                HeadingSectionPreview()
            }
        }

        composeTestRule.onNodeWithText("Important").assertIsDisplayed()
        composeTestRule.onNodeWithText("Your catch records").assertIsDisplayed()
        composeTestRule.onNodeWithText("Create a new catch record").assertIsDisplayed()
    }

    @Test
    fun `loading indicator preview composable renders without crashing`() {
        composeTestRule.setContent { LoadingIndicatorPreview() }

        composeTestRule.onNodeWithTag("home_loading_indicator").assertIsDisplayed()
    }

    @Test
    fun `catch record table row preview renders a single row with its header`() {
        composeTestRule.setContent { CatchRecordTableRowPreview() }

        composeTestRule.onNodeWithText("Created by").assertIsDisplayed()
        composeTestRule.onNodeWithTag(CatchRecordsListSectionTestTags.row("4")).assertIsDisplayed()
    }

    @Test
    fun `help accordions section preview composable renders without crashing`() {
        composeTestRule.setContent { HelpAccordionsSectionPreview() }

        composeTestRule.onNodeWithText("Help with catch recording").assertIsDisplayed()
        composeTestRule.onNodeWithText("Catch record statuses").assertIsDisplayed()
    }

    @Test
    fun `help accordion and status help row preview composables render without crashing`() {
        composeTestRule.setContent {
            Column {
                HelpRecordingAccordionPreview()
                CatchStatusesAccordionPreview()
                StatusHelpRowPreview()
            }
        }

        composeTestRule.onNodeWithText("Help with catch recording").assertIsDisplayed()
        composeTestRule.onNodeWithText("Catch record statuses").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Draft:").onFirst().assertIsDisplayed()
    }

    /** Exercises the `LaunchedEffect`/`delay` auto-dismiss branch, otherwise unreachable within a normal
     * test's real-time duration — advances the Compose test clock past the 6s delay instead. */
    @Test
    fun `offline retry message auto-dismisses itself after its delay`() {
        composeTestRule.mainClock.autoAdvance = false
        var dismissed = false
        composeTestRule.setContent {
            MmoTheme { OfflineRetryMessage(message = "Retry queued", onDismissed = { dismissed = true }) }
        }

        composeTestRule.mainClock.advanceTimeBy(6_000L)
        composeTestRule.waitForIdle()
        assertTrue(dismissed)
    }

    @Test
    fun `sync confirmation message auto-dismisses itself after its delay`() {
        composeTestRule.mainClock.autoAdvance = false
        var dismissed = false
        composeTestRule.setContent {
            MmoTheme { SyncConfirmationMessage(message = "Submitted", onDismissed = { dismissed = true }) }
        }

        composeTestRule.mainClock.advanceTimeBy(6_000L)
        composeTestRule.waitForIdle()
        assertTrue(dismissed)
    }

    @Test
    fun `an idle status also shows the loading indicator`() {
        composeTestRule.setContent {
            MmoTheme {
                HomeScreenContent(
                    state = HomeViewState(status = UiStatus.Idle),
                    currentLanguage = "en",
                    onLanguageToggle = {},
                    isOffline = false,
                    selectedTab = 0,
                    onTabSelected = {},
                    onSignOut = {},
                    onCreateCatchRecord = {},
                    onResumeDraft = {},
                )
            }
        }
        composeTestRule
            .onNodeWithTag(HomeScreenTestTags.TAB_LIST)
            .performScrollToNode(hasTestTag("home_loading_indicator"))
        composeTestRule.onNodeWithTag("home_loading_indicator").assertIsDisplayed()
    }

    @Test
    fun `retrying row shows the pending retry label and a disabled retry button`() {
        composeTestRule.setContent {
            MmoTheme {
                HomeScreenContent(
                    state =
                        HomeViewState(
                            status = UiStatus.Content(HomeSummary("alice", everyStatusRecords)),
                            retryingIds = setOf("4"),
                        ),
                    currentLanguage = "en",
                    onLanguageToggle = {},
                    isOffline = false,
                    selectedTab = 0,
                    onTabSelected = {},
                    onSignOut = {},
                    onCreateCatchRecord = {},
                    onResumeDraft = {},
                )
            }
        }
        scrollToRow("4")
        composeTestRule.onNodeWithTag(CatchRecordsListSectionTestTags.retry("4")).assertIsNotEnabled()
        composeTestRule.onNodeWithText("Retrying…").assertIsDisplayed()
    }

    /** With exactly one page of records, both pagination links are disabled at once (ADR-0014 Phase F). */
    @Test
    fun `pagination links are both disabled when there is only one page`() {
        composeTestRule.setContent {
            MmoTheme {
                LazyColumn {
                    catchRecordsListSection(records = everyStatusRecords, onRecordClick = {})
                }
            }
        }
        composeTestRule.onNodeWithTag(CatchRecordsListSectionTestTags.PAGINATION_PREVIOUS).assertIsNotEnabled()
        composeTestRule.onNodeWithTag(CatchRecordsListSectionTestTags.PAGINATION_NEXT).assertIsNotEnabled()
    }

    /** On the last of several pages, "Next" is disabled while "Previous" stays enabled — the opposite
     * boundary from the first-page case already covered by the "clickable" pagination test above. */
    @Config(qualifiers = "w412dp-h2000dp")
    @Test
    fun `pagination next link is disabled on the last page while previous stays enabled`() {
        val manyRecords =
            (1..11).map {
                CatchRecordSummary(
                    it.toString(),
                    "MMO-REF-$it",
                    "ACHILLES",
                    DmyDate(it, 1, 2026),
                    RecordStatusTag.Submitted,
                )
            }
        composeTestRule.setContent {
            MmoTheme {
                LazyColumn {
                    catchRecordsListSection(records = manyRecords, onRecordClick = {}, currentPage = 1)
                }
            }
        }
        composeTestRule.onNodeWithTag(CatchRecordsListSectionTestTags.PAGINATION_NEXT).assertIsNotEnabled()
        composeTestRule.onNodeWithTag(CatchRecordsListSectionTestTags.PAGINATION_PREVIOUS).performClick()
    }

    /** Drives the stateful [HomeScreen] wrapper with real view models: state collection and language toggle. */
    @Test
    fun `home screen wrapper collects its real view models and wires the language toggle`() =
        runTest {
            val repository = FakeHomeRepository()
            repository.emit(HomeSummary(signedInUserId = "alice", catchRecords = everyStatusRecords))
            val homeViewModel = buildHomeViewModel(repository, dispatcher = StandardTestDispatcher(testScheduler))
            val languageViewModel = AppLanguageViewModel(FakeAppLanguageRepository())
            val connectivityViewModel = ConnectivityViewModel(FakeConnectivityObserver(initiallyOnline = true))
            var signedOut = false

            composeTestRule.setContent {
                MmoTheme {
                    HomeScreen(
                        onSignOut = { signedOut = true },
                        onCreateCatchRecord = {},
                        onResumeDraft = {},
                        viewModel = homeViewModel,
                        languageViewModel = languageViewModel,
                        connectivityViewModel = connectivityViewModel,
                    )
                }
            }
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag(HomeScreenTestTags.SCREEN).assertIsDisplayed()
            composeTestRule.onNodeWithTag(HomeScreenTestTags.TAB_LIST).assertIsDisplayed()
            composeTestRule.onNodeWithTag("offline_banner").assertDoesNotExist()

            composeTestRule.onNodeWithTag(GdsTopAppBarTestTags.LANGUAGE_TOGGLE).performClick()
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()
            assertEquals(AppLanguage.WELSH, languageViewModel.language.value)
            composeTestRule.onNodeWithTag(GdsTopAppBarTestTags.LANGUAGE_TOGGLE).assertTextEquals("ENG")
            assertFalse(signedOut)
        }

    /** Drives the wrapper's tab switching, back-to-home action and the Settings tab's debug section seam. */
    @Test
    fun `home screen wrapper switches tabs via bottom navigation and the back action returns to the home tab`() =
        runTest {
            val repository = FakeHomeRepository()
            repository.emit(HomeSummary(signedInUserId = "alice", catchRecords = everyStatusRecords))
            val homeViewModel = buildHomeViewModel(repository, dispatcher = StandardTestDispatcher(testScheduler))
            val languageViewModel = AppLanguageViewModel(FakeAppLanguageRepository())
            val connectivityViewModel = ConnectivityViewModel(FakeConnectivityObserver(initiallyOnline = true))
            var signedOut = false

            composeTestRule.setContent {
                MmoTheme {
                    HomeScreen(
                        onSignOut = { signedOut = true },
                        onCreateCatchRecord = {},
                        onResumeDraft = {},
                        viewModel = homeViewModel,
                        languageViewModel = languageViewModel,
                        connectivityViewModel = connectivityViewModel,
                    )
                }
            }
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag(MmoBottomNavigationBarTestTags.SETTINGS_TAB).performClick()
            composeTestRule.onNodeWithTag(SettingsScreenTestTags.SCREEN).assertIsDisplayed()

            composeTestRule.onNodeWithTag(MmoBottomNavigationBarTestTags.NOTIFICATIONS_TAB).performClick()
            composeTestRule.onNodeWithTag(HomeScreenTestTags.NOTIFICATIONS_TAB_HEADING).assertIsDisplayed()

            composeTestRule.onNodeWithTag(GdsTopAppBarTestTags.BACK_BUTTON).performClick()
            composeTestRule.onNodeWithTag(HomeScreenTestTags.TAB_LIST).assertIsDisplayed()
            assertFalse("back from a non-home tab must not sign the user out", signedOut)
        }

    /** Covers the wrapper's `onRetry` wiring — dispatching [HomeEvent.RetrySubmission] on the real
     * [HomeViewModel] (not a test-local lambda) and its `retryingIds` round-trip into the rendered row. */
    @Test
    fun `home screen wrapper retry action dispatches RetrySubmission on the real view model`() =
        runTest {
            val repository = FakeHomeRepository()
            repository.emit(HomeSummary(signedInUserId = "alice", catchRecords = everyStatusRecords))
            val syncScheduler = FakeCatchRecordSyncScheduler()
            val homeViewModel =
                buildHomeViewModel(
                    repository,
                    dispatcher = StandardTestDispatcher(testScheduler),
                    syncScheduler = syncScheduler,
                )
            val languageViewModel = AppLanguageViewModel(FakeAppLanguageRepository())
            val connectivityViewModel = ConnectivityViewModel(FakeConnectivityObserver(initiallyOnline = true))

            composeTestRule.setContent {
                MmoTheme {
                    HomeScreen(
                        onSignOut = {},
                        onCreateCatchRecord = {},
                        onResumeDraft = {},
                        viewModel = homeViewModel,
                        languageViewModel = languageViewModel,
                        connectivityViewModel = connectivityViewModel,
                    )
                }
            }
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            scrollToRow("4")
            composeTestRule.onNodeWithTag(CatchRecordsListSectionTestTags.retry("4")).performClick()
            testScheduler.advanceUntilIdle()
            composeTestRule.waitForIdle()

            assertEquals(listOf("4"), syncScheduler.scheduledDraftIds)
            scrollToRow("4")
            composeTestRule.onNodeWithTag(CatchRecordsListSectionTestTags.retry("4")).assertIsNotEnabled()
        }
}
