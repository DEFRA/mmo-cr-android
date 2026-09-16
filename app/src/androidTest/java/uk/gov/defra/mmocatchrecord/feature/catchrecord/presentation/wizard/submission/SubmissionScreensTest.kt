package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.submission

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft

/**
 * Phase 8, screens 3 & 4 — the online-success and offline/pending-sync submission-result screens. Only
 * the pure, viewModel-free `*ScreenContent` composables are exercised here (see the `Screen`/`ScreenContent`
 * split noted on [LateSubmissionWarningScreenTest]).
 */
class SubmissionScreensTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun draft(reference: String = "A1234520260727150815") =
        CatchRecordDraft(
            id = "draft-1",
            vesselId = "vessel-1",
            modifiedAtEpochMillis = 0L,
            catchRecordReference = reference,
        )

    // --- Screen 3: online submission success -----------------------------------------------------

    @Test
    fun successScreenShowsBannerReferenceAndWhatHappensNextBullets() {
        composeTestRule.setContent {
            MmoTheme {
                SubmissionSuccessScreenContent(
                    draft = draft(),
                    onViewRecords = {},
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            }
        }

        composeTestRule.onNodeWithTag(SubmissionSuccessScreenTestTags.BANNER).assertIsDisplayed()
        composeTestRule.onNodeWithText("Your catch record has been submitted").assertIsDisplayed()
        composeTestRule.onNodeWithText("Your catch record reference A1234520260727150815").assertIsDisplayed()
        composeTestRule
            .onNodeWithText(
                "Your catch record has been received by the relevant fishing authority",
                substring = true,
            ).performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("You'll receive a confirmation email within 24 hours", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(SubmissionSuccessScreenTestTags.VIEW_RECORDS_ACTION)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun successScreenBannerTextConveysMeaningWithoutRelyingOnColourAlone() {
        // WCAG 2.2 AA: the banner's success/failure meaning must be readable as plain text, not only
        // inferred from its green background colour — see GdsResultBanner's own doc comment.
        composeTestRule.setContent {
            MmoTheme {
                SubmissionSuccessScreenContent(
                    draft = draft(),
                    onViewRecords = {},
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            }
        }

        composeTestRule.onNodeWithText("Your catch record has been submitted").assertIsDisplayed()
    }

    @Test
    fun tappingViewYourCatchRecordsOnSuccessScreenInvokesCallback() {
        var tapped = false
        composeTestRule.setContent {
            MmoTheme {
                SubmissionSuccessScreenContent(
                    draft = draft(),
                    onViewRecords = { tapped = true },
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            }
        }

        composeTestRule
            .onNodeWithTag(SubmissionSuccessScreenTestTags.VIEW_RECORDS_ACTION)
            .performScrollTo()
            .performClick()

        assertTrue(tapped)
    }

    // --- Screen 4: offline/pending-sync -----------------------------------------------------------

    @Test
    fun pendingSyncScreenShowsBannerReferenceBodyAndWarning() {
        composeTestRule.setContent {
            MmoTheme {
                SubmissionPendingSyncScreenContent(
                    draft = draft(),
                    onViewRecords = {},
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            }
        }

        composeTestRule.onNodeWithTag(SubmissionPendingSyncScreenTestTags.BANNER).assertIsDisplayed()
        composeTestRule.onNodeWithText("Your catch record has been recorded").assertIsDisplayed()
        composeTestRule.onNodeWithText("Your catch record reference A1234520260727150815").assertIsDisplayed()
        composeTestRule
            .onNodeWithText(
                "Your catch record has been saved and will be sent automatically when you next have a " +
                    "mobile signal or Wi-Fi connection.",
            ).performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(SubmissionPendingSyncScreenTestTags.CHECK_RECORDS_LINK)
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(SubmissionPendingSyncScreenTestTags.WARNING)
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("You must record your catch within 24 hours of landing.")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(SubmissionPendingSyncScreenTestTags.VIEW_RECORDS_ACTION)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun pendingSyncWarningConveysMeaningThroughTextNotIconOrColourAlone() {
        // WCAG 2.2 AA: GdsWarningText pairs its triangle icon with bold text, never relying on the icon or
        // any colour alone — the exact same information must be present as plain, readable text.
        composeTestRule.setContent {
            MmoTheme {
                SubmissionPendingSyncScreenContent(
                    draft = draft(),
                    onViewRecords = {},
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            }
        }

        composeTestRule
            .onNodeWithTag(SubmissionPendingSyncScreenTestTags.WARNING)
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("You must record your catch within 24 hours of landing.")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun tappingCheckYourCatchRecordsLinkInvokesCallback() {
        var tapped = false
        composeTestRule.setContent {
            MmoTheme {
                SubmissionPendingSyncScreenContent(
                    draft = draft(),
                    onViewRecords = { tapped = true },
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            }
        }

        composeTestRule
            .onNodeWithTag(SubmissionPendingSyncScreenTestTags.CHECK_RECORDS_LINK)
            .performScrollTo()
            .performClick()

        assertTrue(tapped)
    }

    @Test
    fun tappingViewYourCatchRecordsOnPendingSyncScreenInvokesCallback() {
        var tapped = false
        composeTestRule.setContent {
            MmoTheme {
                SubmissionPendingSyncScreenContent(
                    draft = draft(),
                    onViewRecords = { tapped = true },
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            }
        }

        composeTestRule
            .onNodeWithTag(SubmissionPendingSyncScreenTestTags.VIEW_RECORDS_ACTION)
            .performScrollTo()
            .performClick()

        assertTrue(tapped)
    }
}
