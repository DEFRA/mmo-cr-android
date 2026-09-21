package uk.gov.defra.mmocatchrecord.feature.home.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme

/**
 * Instrumented tests for the "Your settings" screen ([SettingsTabContent]) — the Settings tab of the
 * app's bottom navigation. Elements are driven by stable [SettingsScreenTestTags], per the testing
 * instructions, rather than display text alone.
 */
class SettingsScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun settingsScreenDisplaysTitleAndLinks() {
        composeTestRule.setContent {
            MmoTheme {
                SettingsTabContent(onSignOut = {})
            }
        }

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.TITLE).assertIsDisplayed()
        composeTestRule.onNodeWithTag(SettingsScreenTestTags.MY_ACCOUNT_LINK).assertIsDisplayed()
        composeTestRule.onNodeWithTag(SettingsScreenTestTags.PRIVACY_NOTICE_LINK).assertIsDisplayed()
        composeTestRule.onNodeWithTag(SettingsScreenTestTags.SUPPORT_INFORMATION_LINK).assertIsDisplayed()
        composeTestRule.onNodeWithTag(SettingsScreenTestTags.SIGN_OUT_LINK).assertIsDisplayed()
    }

    @Test
    fun analyticsToggleStartsOffAndTogglesOn() {
        composeTestRule.setContent {
            MmoTheme {
                SettingsTabContent(onSignOut = {})
            }
        }

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.ANALYTICS_TOGGLE).assertIsOff()
        composeTestRule.onNodeWithTag(SettingsScreenTestTags.ANALYTICS_TOGGLE).performClick()
        composeTestRule.onNodeWithTag(SettingsScreenTestTags.ANALYTICS_TOGGLE).assertIsOn()
    }

    @Test
    fun signOutLinkInvokesCallback() {
        var signedOut = false
        composeTestRule.setContent {
            MmoTheme {
                SettingsTabContent(onSignOut = { signedOut = true })
            }
        }

        composeTestRule.onNodeWithTag(SettingsScreenTestTags.SIGN_OUT_LINK).performClick()

        assert(signedOut)
    }
}
