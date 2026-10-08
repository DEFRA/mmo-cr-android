package uk.gov.defra.mmocatchrecord.feature.home.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.common.design.RecordStatusTag
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DmyDate
import uk.gov.defra.mmocatchrecord.feature.home.domain.CatchRecordSummary
import uk.gov.defra.mmocatchrecord.feature.home.domain.HomeSummary

/**
 * JVM/Robolectric Compose coverage for the Home records list and offline banner — required because
 * `connectedDebugAndroidTest` coverage is not merged into Kover/SonarCloud (see ADR 0014 Phase B).
 */
@RunWith(RobolectricTestRunner::class)
class HomeScreenRobolectricTests {
    @get:Rule
    val composeTestRule = createComposeRule()

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
}
