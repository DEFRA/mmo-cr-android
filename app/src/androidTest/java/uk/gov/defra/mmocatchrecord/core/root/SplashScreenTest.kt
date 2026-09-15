package uk.gov.defra.mmocatchrecord.core.root

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme

/**
 * UI test for [SplashScreen] — the [RootPhase.SPLASH] destination shown while [SessionCoordinator]
 * resolves the real initial phase. Asserts the stable root test tag and that the GOV.UK wordmark is
 * present with an accessible content description, per testing.instructions ("stable testTags/semantics,
 * not display text alone").
 */
class SplashScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun splashScreenShowsRootTagAndWordmarkContentDescription() {
        composeTestRule.setContent {
            MmoTheme {
                SplashScreen()
            }
        }

        composeTestRule
            .onNodeWithTag(RootScreenTestTags.SPLASH_SCREEN)
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithContentDescription("GOV.UK")
            .assertIsDisplayed()
    }
}
