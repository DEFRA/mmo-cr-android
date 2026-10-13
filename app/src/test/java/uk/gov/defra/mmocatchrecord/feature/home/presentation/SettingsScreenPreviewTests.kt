package uk.gov.defra.mmocatchrecord.feature.home.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** `SettingsTabContentPreview` otherwise stays uncounted new code - see `HomeScreenRobolectricTests`'s
 *  identically motivated preview-rendering tests for the established pattern this mirrors. */
@RunWith(RobolectricTestRunner::class)
class SettingsScreenPreviewTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `settings tab content preview renders without crashing`() {
        composeTestRule.setContent { SettingsTabContentPreview() }

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.TITLE).assertIsDisplayed()
    }
}
