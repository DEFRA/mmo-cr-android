package uk.gov.defra.mmocatchrecord.feature.home.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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
    ) {
        val state = HomeViewState(status = UiStatus.Content(HomeSummary("alice", records)))
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
}
