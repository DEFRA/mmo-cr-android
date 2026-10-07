package uk.gov.defra.mmocatchrecord.common.design

import android.content.Context
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import uk.gov.defra.mmocatchrecord.R

/** BR-XX (ADR 0014, Phase D): on-device WCAG 2.2 AA pass for [UnsavedChangesDialog]. */
class UnsavedChangesDialogTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun string(resId: Int): String = ApplicationProvider.getApplicationContext<Context>().getString(resId)

    @Test
    fun titleBodyAndActionsAreAnnouncedAndMeetTouchTargetSize() {
        composeTestRule.setContent {
            MmoTheme {
                UnsavedChangesDialog(onStay = {}, onLeave = {})
            }
        }

        composeTestRule.onNodeWithTag(UnsavedChangesDialogTestTags.DIALOG).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.unsaved_changes_dialog_title)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.unsaved_changes_dialog_body)).assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(UnsavedChangesDialogTestTags.LEAVE_ACTION)
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
        composeTestRule
            .onNodeWithTag(UnsavedChangesDialogTestTags.STAY_ACTION)
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
    }

    @Test
    fun systemBackPressDismissesAsStayNeverAsLeave() {
        var staySelected = false
        var leaveSelected = false
        composeTestRule.setContent {
            MmoTheme {
                UnsavedChangesDialog(onStay = { staySelected = true }, onLeave = { leaveSelected = true })
            }
        }

        Espresso.pressBack()
        composeTestRule.waitForIdle()

        assertFalse(leaveSelected)
        assertTrue(staySelected)
    }

    @Test
    fun tappingLeaveInvokesOnLeaveOnly() {
        var staySelected = false
        var leaveSelected = false
        composeTestRule.setContent {
            MmoTheme {
                UnsavedChangesDialog(onStay = { staySelected = true }, onLeave = { leaveSelected = true })
            }
        }

        composeTestRule.onNodeWithTag(UnsavedChangesDialogTestTags.LEAVE_ACTION).performClick()
        composeTestRule.waitForIdle()

        assertFalse(staySelected)
        assertTrue(leaveSelected)
    }
}
