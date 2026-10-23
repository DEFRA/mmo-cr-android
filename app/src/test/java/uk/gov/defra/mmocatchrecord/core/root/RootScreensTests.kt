package uk.gov.defra.mmocatchrecord.core.root

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme

/** JVM/Robolectric tests for [AppLockScreen] (and its private `PlaceholderScreen` backing composable). */
@RunWith(RobolectricTestRunner::class)
class RootScreensTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `app lock screen shows its title, body and triggers onUnlock when clicked`() {
        var unlocked = false
        composeTestRule.setContent {
            MmoTheme {
                AppLockScreen(onUnlock = { unlocked = true })
            }
        }

        composeTestRule.onNodeWithTag(RootScreenTestTags.APP_LOCK_SCREEN).assertIsDisplayed()
        composeTestRule.onNodeWithText("App locked").assertIsDisplayed()
        composeTestRule.onNodeWithText("Unlock the app to continue.").assertIsDisplayed()
        composeTestRule.onNodeWithTag(RootScreenTestTags.APP_LOCK_ACTION).performClick()

        assertTrue(unlocked)
    }

    @Test
    fun `app lock screen preview renders without throwing`() {
        composeTestRule.setContent {
            AppLockScreenPreview()
        }

        composeTestRule.onNodeWithTag(RootScreenTestTags.APP_LOCK_SCREEN).assertIsDisplayed()
    }
}
