package uk.gov.defra.mmocatchrecord.core.root

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme

/** JVM/Robolectric tests for [SplashScreen], the `testDevDebugUnitTest`-reachable [RootPhase.SPLASH] destination. */
@RunWith(RobolectricTestRunner::class)
class SplashScreenTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `splash screen shows the GOV UK wordmark and crown`() {
        composeTestRule.setContent {
            MmoTheme {
                SplashScreen()
            }
        }

        composeTestRule.onNodeWithTag(RootScreenTestTags.SPLASH_SCREEN).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("GOV.UK").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("GOV.UK Crown Logo").assertIsDisplayed()
    }

    @Test
    fun `splash screen preview renders without throwing`() {
        composeTestRule.setContent {
            SplashScreenPreview()
        }

        composeTestRule.onNodeWithTag(RootScreenTestTags.SPLASH_SCREEN).assertIsDisplayed()
    }
}
