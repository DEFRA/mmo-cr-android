package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.common.design.UnsavedChangesDialogTestTags

/**
 * BR-XX (ADR 0014, Phase D) coverage for [CatchRecordWizardScaffold]'s back/dialog mechanism. A sibling,
 * lower-priority [BackHandler] stands in for the real NavHost's own back handling that would otherwise sit
 * above this screen, so "navigation proceeds" is observable exactly as it is in production.
 */
@RunWith(RobolectricTestRunner::class)
class CatchRecordWizardScaffoldTests {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private var onBackCalls = 0
    private var onDiscardCalls = 0

    private fun setContent(hasUnsavedChanges: Boolean) {
        composeTestRule.setContent {
            WizardTestTheme {
                BackHandler(enabled = true) { onBackCalls++ }
                CatchRecordWizardScaffold(
                    screenTestTag = "scaffold",
                    title = "Title",
                    onBack = { onBackCalls++ },
                    unsavedChanges =
                        WizardUnsavedChangesConfig(
                            hasUnsavedChanges = hasUnsavedChanges,
                            onDiscardChanges = { onDiscardCalls++ },
                        ),
                ) {
                    Text("content")
                }
            }
        }
    }

    private fun pressSystemBack() {
        composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
    }

    @Test
    fun cleanStateBackPressShowsNoDialogAndNavigationProceeds() {
        setContent(hasUnsavedChanges = false)

        pressSystemBack()

        composeTestRule.onNodeWithTag(UnsavedChangesDialogTestTags.DIALOG).assertDoesNotExist()
        assertEquals(1, onBackCalls)
    }

    @Test
    fun dirtyStateBackPressShowsDialogAndBlocksNavigation() {
        setContent(hasUnsavedChanges = true)

        pressSystemBack()

        composeTestRule.onNodeWithTag(UnsavedChangesDialogTestTags.DIALOG).assertIsDisplayed()
        assertEquals(0, onBackCalls)
    }

    @Test
    fun stayOnThisPageDismissesDialogAndKeepsScreen() {
        setContent(hasUnsavedChanges = true)
        pressSystemBack()

        composeTestRule.onNodeWithTag(UnsavedChangesDialogTestTags.STAY_ACTION).performClick()

        composeTestRule.onNodeWithTag(UnsavedChangesDialogTestTags.DIALOG).assertDoesNotExist()
        assertEquals(0, onBackCalls)
        assertEquals(0, onDiscardCalls)
    }

    @Test
    fun leaveWithoutSavingNavigatesAwayAndDiscards() {
        setContent(hasUnsavedChanges = true)
        pressSystemBack()

        composeTestRule.onNodeWithTag(UnsavedChangesDialogTestTags.LEAVE_ACTION).performClick()

        composeTestRule.onNodeWithTag(UnsavedChangesDialogTestTags.DIALOG).assertDoesNotExist()
        assertEquals(1, onBackCalls)
        assertEquals(1, onDiscardCalls)
    }

    @Test
    fun dialogTitleIsAnnouncedAndActionsMeetMinimumTouchTarget() {
        setContent(hasUnsavedChanges = true)
        pressSystemBack()

        val title = composeTestRule.activity.getString(R.string.unsaved_changes_dialog_title)
        val body = composeTestRule.activity.getString(R.string.unsaved_changes_dialog_body)
        composeTestRule.onNodeWithText(title).assertIsDisplayed()
        composeTestRule.onNodeWithText(body).assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(UnsavedChangesDialogTestTags.STAY_ACTION)
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
        composeTestRule
            .onNodeWithTag(UnsavedChangesDialogTestTags.LEAVE_ACTION)
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
    }
}
