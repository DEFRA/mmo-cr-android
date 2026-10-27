package uk.gov.defra.mmocatchrecord.feature.home.presentation

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.common.design.RecordStatusTag
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DmyDate
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardTestTheme
import uk.gov.defra.mmocatchrecord.feature.home.domain.CatchRecordSummary
import uk.gov.defra.mmocatchrecord.feature.home.domain.HomeSummary

/** Offline-first draft/sync story — record list statuses (FR5, Scenario 5), resume (FR2), FR4 and FR9. */
@RunWith(AndroidJUnit4::class)
class CatchRecordStatusListTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val draft = CatchRecordSummary("draft-1", null, "ACHILLES", null, RecordStatusTag.Draft)
    private val readyToSubmit =
        CatchRecordSummary("ready-1", "REF-002", "ACHILLES", DmyDate(18, 11, 2020), RecordStatusTag.ReadyToSubmit)
    private val awaitingSync =
        CatchRecordSummary("unsent-1", "REF-003", "ACHILLES", DmyDate(15, 11, 2020), RecordStatusTag.AwaitingSync)
    private val submitted =
        CatchRecordSummary("submitted-1", "REF-004", "ACHILLES", DmyDate(10, 11, 2020), RecordStatusTag.Submitted)

    private fun string(resId: Int): String = ApplicationProvider.getApplicationContext<Context>().getString(resId)

    private fun setHome(
        records: List<CatchRecordSummary> = listOf(draft, readyToSubmit, awaitingSync, submitted),
        isOffline: () -> Boolean = { false },
        syncConfirmationMessage: String? = null,
        onResumeDraft: (String) -> Unit = {},
        onDismissSyncConfirmationMessage: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            WizardTestTheme {
                HomeScreenContent(
                    state =
                        HomeViewState(
                            status = UiStatus.Content(HomeSummary("user-1", records)),
                            syncConfirmationMessage = syncConfirmationMessage,
                        ),
                    currentLanguage = "en",
                    onLanguageToggle = {},
                    isOffline = isOffline(),
                    selectedTab = 0,
                    onTabSelected = {},
                    onSignOut = {},
                    onCreateCatchRecord = {},
                    onResumeDraft = onResumeDraft,
                    onDismissSyncConfirmationMessage = onDismissSyncConfirmationMessage,
                )
            }
        }
    }

    private fun scrollToRow(record: CatchRecordSummary) {
        composeTestRule
            .onNodeWithTag(HomeScreenTestTags.TAB_LIST)
            .performScrollToNode(hasTestTag(CatchRecordsListSectionTestTags.row(record.id)))
    }

    private fun textInRow(
        record: CatchRecordSummary,
        text: String,
    ) = composeTestRule.onNode(
        hasText(text) and hasAnyAncestor(hasTestTag(CatchRecordsListSectionTestTags.row(record.id))),
        useUnmergedTree = true,
    )

    // --- Scenario 5 / FR5: each record clearly shows its status ------------------------------------------

    @Test
    fun recordsListShowsTheCorrectStatusTagOnEveryRecord() {
        setHome()

        mapOf(
            draft to R.string.status_draft,
            readyToSubmit to R.string.status_ready_to_submit,
            awaitingSync to R.string.status_awaiting_sync,
            submitted to R.string.status_submitted,
        ).forEach { (record, statusLabel) ->
            scrollToRow(record)
            textInRow(record, string(statusLabel)).assertIsDisplayed()
        }
    }

    @Test
    fun eachStatusUsesADistinctTextLabelNotColourAlone() {
        val labels =
            listOf(
                R.string.status_draft,
                R.string.status_ready_to_submit,
                R.string.status_awaiting_sync,
                R.string.status_submitted,
            ).map(::string)

        assertEquals("Status labels must be unique so records are distinguishable", 4, labels.toSet().size)
        assertTrue(labels.none { it.isBlank() })
    }

    @Test
    fun draftWithNoReturnDateShowsNotEnteredYetPlaceholder() {
        setHome()

        scrollToRow(draft)
        textInRow(draft, string(R.string.records_trip_end_date_not_set)).assertIsDisplayed()
    }

    // --- FR2 / Scenario 1: drafts can be reopened; sent/queued records cannot ----------------------------

    @Test
    fun tappingADraftRecordReopensThatDraft() {
        var resumedId: String? = null
        setHome(onResumeDraft = { resumedId = it })

        scrollToRow(draft)
        textInRow(draft, string(R.string.records_trip_end_date_not_set)).assertHasClickAction().performClick()

        assertEquals(draft.id, resumedId)
    }

    @Test
    fun tappingAReadyToSubmitRecordReopensThatRecord() {
        var resumedId: String? = null
        setHome(onResumeDraft = { resumedId = it })

        scrollToRow(readyToSubmit)
        textInRow(readyToSubmit, "18 Nov 2020").assertHasClickAction().performClick()

        assertEquals(readyToSubmit.id, resumedId)
    }

    @Test
    fun awaitingSyncAndSubmittedRecordsAreNotEditable() {
        setHome()

        scrollToRow(awaitingSync)
        textInRow(awaitingSync, "15 Nov 2020").assertHasNoClickAction()
        scrollToRow(submitted)
        textInRow(submitted, "10 Nov 2020").assertHasNoClickAction()
    }

    // --- FR4: online/offline indicator ------------------------------------------------------------------

    @Test
    fun offlineIndicatorAppearsWhenConnectivityIsLostAndClearsWhenRestored() {
        var isOffline by mutableStateOf(false)
        setHome(isOffline = { isOffline })

        composeTestRule.onNodeWithTag("offline_banner").assertDoesNotExist()

        isOffline = true
        composeTestRule.onNodeWithTag("offline_banner").assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.offline_banner_label), substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.offline_banner_message), substring = true).assertIsDisplayed()

        isOffline = false
        composeTestRule.onNodeWithTag("offline_banner").assertDoesNotExist()
    }

    @Test
    fun recordsRemainVisibleWhileOffline() {
        setHome(isOffline = { true })

        scrollToRow(awaitingSync)
        textInRow(awaitingSync, string(R.string.status_awaiting_sync)).assertIsDisplayed()
    }

    // --- FR9: confirmation once a waiting record has been sent ------------------------------------------

    @Test
    fun syncConfirmationMessageIsShownAndAnnouncedToScreenReaders() {
        setHome(syncConfirmationMessage = "Catch record REF-003 has been submitted")

        composeTestRule
            .onNodeWithTag(SyncConfirmationMessageTestTags.MESSAGE)
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        composeTestRule.onNodeWithText("Catch record REF-003 has been submitted").assertIsDisplayed()
    }

    @Test
    fun syncConfirmationMessageDismissesItselfAfterADelay() {
        var dismissed = false
        composeTestRule.mainClock.autoAdvance = false
        setHome(
            syncConfirmationMessage = "Catch record REF-003 has been submitted",
            onDismissSyncConfirmationMessage = { dismissed = true },
        )

        composeTestRule.mainClock.advanceTimeBy(1_000L)
        assertFalse("Message must stay long enough to be read", dismissed)
        composeTestRule.mainClock.advanceTimeBy(SYNC_CONFIRMATION_DISMISS_MS)

        assertTrue(dismissed)
    }

    @Test
    fun noSyncConfirmationMessageWhenNothingHasBeenSent() {
        setHome(syncConfirmationMessage = null)

        composeTestRule.onNodeWithTag(SyncConfirmationMessageTestTags.MESSAGE).assertDoesNotExist()
    }

    // --- Status guidance -----------------------------------------------------------------------------------

    @Test
    fun catchRecordStatusesHelpExplainsEveryStatus() {
        setHome()

        composeTestRule
            .onNodeWithTag(HomeScreenTestTags.TAB_LIST)
            .performScrollToNode(hasText(string(R.string.catch_record_statuses)))
        composeTestRule.onNodeWithText(string(R.string.catch_record_statuses)).performClick()

        listOf(
            R.string.status_draft_desc,
            R.string.status_ready_to_submit_desc,
            R.string.status_awaiting_sync_desc,
            R.string.status_submitted_desc,
        ).forEach { descRes ->
            composeTestRule
                .onNodeWithTag(HomeScreenTestTags.TAB_LIST)
                .performScrollToNode(hasText(string(descRes)))
            composeTestRule.onNodeWithText(string(descRes)).assertIsDisplayed()
        }
    }

    @Test
    fun emptyRecordsListShowsEmptyState() {
        setHome(records = emptyList())

        composeTestRule
            .onNodeWithTag(HomeScreenTestTags.TAB_LIST)
            .performScrollToNode(hasTestTag(CatchRecordsListSectionTestTags.EMPTY_STATE))
        composeTestRule.onNodeWithTag(CatchRecordsListSectionTestTags.EMPTY_STATE).assertIsDisplayed()
    }

    private companion object {
        const val SYNC_CONFIRMATION_DISMISS_MS = 6_000L
    }
}
