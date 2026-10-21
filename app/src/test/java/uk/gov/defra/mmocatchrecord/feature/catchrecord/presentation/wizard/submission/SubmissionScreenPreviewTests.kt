package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.submission

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Smoke-renders the submission screens' in-file `@Preview`s so they stay buildable and are covered. */
@RunWith(RobolectricTestRunner::class)
class SubmissionScreenPreviewTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun checkYourAnswersPreviewRendersWithoutThrowing() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                CheckYourAnswersScreenContentPreview()
            }
        }
    }

    @Test
    fun checkYourAnswersOfflinePreviewShowsTheOfflineNotice() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                CheckYourAnswersScreenContentOfflinePreview()
            }
        }

        composeTestRule.onNodeWithTag(CheckYourAnswersScreenTestTags.OFFLINE_NOTICE).assertExists()
    }

    @Test
    fun lateSubmissionWarningPreviewShowsTheSaveAction() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                LateSubmissionWarningScreenPreview()
            }
        }

        composeTestRule.onNodeWithTag(LateSubmissionWarningScreenTestTags.SAVE_ACTION).assertExists()
    }
}
