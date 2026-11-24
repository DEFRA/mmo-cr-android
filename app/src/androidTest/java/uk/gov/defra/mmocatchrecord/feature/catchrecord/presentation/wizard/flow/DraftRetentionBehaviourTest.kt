package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.common.design.GdsTopAppBarTestTags
import uk.gov.defra.mmocatchrecord.common.design.MmoBottomNavigationBarTestTags
import uk.gov.defra.mmocatchrecord.common.design.UnsavedChangesDialogTestTags

/** BR-XX draft retention: only "Save and continue" persists; leaving with unsaved input warns first. */
@RunWith(AndroidJUnit4::class)
class DraftRetentionBehaviourTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private class Callbacks {
        var backCount = 0
        var discardCount = 0
        var navigatedTab: Int? = null
    }

    private fun string(resId: Int): String = ApplicationProvider.getApplicationContext<Context>().getString(resId)

    private fun setWizardStep(
        hasUnsavedChanges: Boolean,
        callbacks: Callbacks,
        isOffline: Boolean = false,
    ) {
        composeTestRule.setContent {
            WizardTestTheme(isOffline = isOffline) {
                CompositionLocalProvider(LocalWizardBottomNavAction provides { callbacks.navigatedTab = it }) {
                    CatchRecordWizardScaffold(
                        screenTestTag = SCREEN_TAG,
                        title = "When did you leave port?",
                        onBack = { callbacks.backCount++ },
                        unsavedChanges =
                            WizardUnsavedChangesConfig(
                                hasUnsavedChanges = hasUnsavedChanges,
                                onDiscardChanges = { callbacks.discardCount++ },
                            ),
                    ) {
                        Text("Form body")
                    }
                }
            }
        }
    }

    private fun dialog() = composeTestRule.onNodeWithTag(UnsavedChangesDialogTestTags.DIALOG)

    // --- BR-XX: user is warned before unsaved data is lost ----------------------------------------------

    @Test
    fun systemBackWithUnsavedInputWarnsThatDataWillNotBeSaved() {
        val callbacks = Callbacks()
        setWizardStep(hasUnsavedChanges = true, callbacks = callbacks)

        Espresso.pressBack()

        dialog().assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.unsaved_changes_dialog_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.unsaved_changes_dialog_body)).assertIsDisplayed()
        assertEquals("Must not leave until the user confirms", 0, callbacks.backCount)
    }

    @Test
    fun topBarBackWithUnsavedInputWarnsBeforeLeaving() {
        val callbacks = Callbacks()
        setWizardStep(hasUnsavedChanges = true, callbacks = callbacks)

        composeTestRule.onNodeWithTag(GdsTopAppBarTestTags.BACK_BUTTON).performClick()

        dialog().assertIsDisplayed()
        assertEquals(0, callbacks.backCount)
    }

    @Test
    fun choosingStayKeepsTheUserOnTheStepWithTheirInput() {
        val callbacks = Callbacks()
        setWizardStep(hasUnsavedChanges = true, callbacks = callbacks)

        Espresso.pressBack()
        composeTestRule.onNodeWithTag(UnsavedChangesDialogTestTags.STAY_ACTION).performClick()

        dialog().assertDoesNotExist()
        composeTestRule.onNodeWithText("Form body").assertIsDisplayed()
        assertEquals(0, callbacks.backCount)
        assertEquals(0, callbacks.discardCount)
    }

    @Test
    fun choosingLeaveDiscardsUnsavedInputThenNavigatesBack() {
        val callbacks = Callbacks()
        setWizardStep(hasUnsavedChanges = true, callbacks = callbacks)

        Espresso.pressBack()
        composeTestRule.onNodeWithTag(UnsavedChangesDialogTestTags.LEAVE_ACTION).performClick()

        assertEquals(1, callbacks.discardCount)
        assertEquals(1, callbacks.backCount)
    }

    @Test
    fun switchingTabWithUnsavedInputWarnsAndOnlyNavigatesAfterLeave() {
        val callbacks = Callbacks()
        setWizardStep(hasUnsavedChanges = true, callbacks = callbacks)

        composeTestRule.onNodeWithTag(MmoBottomNavigationBarTestTags.HOME_TAB).performClick()
        dialog().assertIsDisplayed()
        assertNull(callbacks.navigatedTab)

        composeTestRule.onNodeWithTag(UnsavedChangesDialogTestTags.LEAVE_ACTION).performClick()

        assertEquals(0, callbacks.navigatedTab)
        assertEquals(1, callbacks.discardCount)
    }

    @Test
    fun leavingWithNothingUnsavedDoesNotWarn() {
        val callbacks = Callbacks()
        setWizardStep(hasUnsavedChanges = false, callbacks = callbacks)

        composeTestRule.onNodeWithTag(GdsTopAppBarTestTags.BACK_BUTTON).performClick()

        dialog().assertDoesNotExist()
        assertEquals(1, callbacks.backCount)
        assertEquals(0, callbacks.discardCount)
    }

    @Test
    fun unsavedChangesWarningStillAppliesWhileOffline() {
        val callbacks = Callbacks()
        setWizardStep(hasUnsavedChanges = true, callbacks = callbacks, isOffline = true)

        composeTestRule.onNodeWithTag("offline_banner").assertIsDisplayed()
        Espresso.pressBack()

        dialog().assertIsDisplayed()
    }

    // --- FR2 / Scenario 1: continue a saved draft -------------------------------------------------------

    @Test
    fun choosingCompleteOnDraftResumeContinuesTheDraft() {
        var resumed = false
        var deleted = false
        composeTestRule.setContent {
            WizardTestTheme {
                DraftResumeScreenContent(onResume = { resumed = true }, onDeleteConfirmed = { deleted = true })
            }
        }

        composeTestRule.onNodeWithTag("${DraftResumeScreenTestTags.OPTION_PREFIX}_0").performClick()
        composeTestRule.onNodeWithTag(DraftResumeScreenTestTags.SAVE_ACTION).performClick()

        assertTrue(resumed)
        assertFalse(deleted)
    }

    @Test
    fun draftResumeWithoutAChoiceShowsErrorAndKeepsTheDraft() {
        var resumed = false
        var deleted = false
        composeTestRule.setContent {
            WizardTestTheme {
                DraftResumeScreenContent(onResume = { resumed = true }, onDeleteConfirmed = { deleted = true })
            }
        }

        composeTestRule.onNodeWithTag(DraftResumeScreenTestTags.SAVE_ACTION).performClick()

        composeTestRule.onNodeWithTag(DraftResumeScreenTestTags.ERROR_MESSAGE).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.wizard_select_action_error)).assertIsDisplayed()
        assertFalse(resumed)
        assertFalse(deleted)
    }

    @Test
    fun cancellingDeleteOnDraftResumeKeepsTheDraft() {
        var deleted = false
        composeTestRule.setContent {
            WizardTestTheme {
                DraftResumeScreenContent(onResume = {}, onDeleteConfirmed = { deleted = true })
            }
        }

        composeTestRule.onNodeWithTag("${DraftResumeScreenTestTags.OPTION_PREFIX}_1").performClick()
        composeTestRule.onNodeWithTag(DraftResumeScreenTestTags.SAVE_ACTION).performClick()
        composeTestRule.onNodeWithTag(DraftResumeScreenTestTags.CANCEL_DELETE).performClick()

        composeTestRule.onNodeWithTag(DraftResumeScreenTestTags.CONFIRM_DIALOG).assertDoesNotExist()
        assertFalse(deleted)
    }

    private companion object {
        const val SCREEN_TAG = "draft_retention_step"
    }
}
