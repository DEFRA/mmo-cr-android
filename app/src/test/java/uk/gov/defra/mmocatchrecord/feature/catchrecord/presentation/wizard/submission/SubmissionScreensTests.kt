package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.submission

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft

/**
 * JVM/Robolectric port of the androidTest `SubmissionScreensTest` — see that file's doc comments for the
 * full rationale. Coverage from `connectedDebugAndroidTest` is not merged into the Kover/SonarCloud report
 * that backs this PR's quality gate, so the pure `*ScreenContent` composables need an equivalent
 * `testDebugUnitTest` exerciser as well. Also exercises each screen's `@Preview` composable directly (both
 * of which this PR reformatted the constructor-call layout of, per ktlint) so that reformatting is covered
 * too, not just the underlying `*ScreenContent` logic.
 */
@RunWith(RobolectricTestRunner::class)
class SubmissionScreensTests {
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

    @Test
    fun successScreenPreviewRendersWithoutError() {
        // CatchRecordWizardScaffold resolves its language/connectivity state from Hilt view models unless
        // LocalInspectionMode is true (see that composable's doc comment) — providing it here mirrors
        // exactly what Android Studio's own @Preview renderer does, letting this Preview composable run
        // under plain Robolectric without a Hilt component.
        composeTestRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                SubmissionSuccessScreenPreview()
            }
        }

        composeTestRule.onNodeWithTag(SubmissionSuccessScreenTestTags.BANNER).assertIsDisplayed()
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

    @Test
    fun pendingSyncScreenContentPreviewRendersWithoutError() {
        composeTestRule.setContent {
            SubmissionPendingSyncScreenContentPreview()
        }

        composeTestRule.onNodeWithTag(SubmissionPendingSyncScreenTestTags.BANNER).assertIsDisplayed()
    }

    // --- Phase 8, screen 1: late submission warning -----------------------------------------------

    @Test
    fun lateSubmissionWarningScreenContentInvokesBothActionCallbacks() {
        var checkedTripEndDate = false
        var submitted = false
        composeTestRule.setContent {
            MmoTheme {
                LateSubmissionWarningScreenContent(
                    onCheckTripEndDate = { checkedTripEndDate = true },
                    onSubmit = { submitted = true },
                )
            }
        }

        composeTestRule
            .onNodeWithTag(LateSubmissionWarningScreenTestTags.CHECK_TRIP_END_DATE_LINK)
            .performClick()
        composeTestRule.onNodeWithTag(LateSubmissionWarningScreenTestTags.SAVE_ACTION).performClick()

        assertTrue(checkedTripEndDate)
        assertTrue(submitted)
    }
}
