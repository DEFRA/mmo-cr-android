package uk.gov.defra.mmocatchrecord

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.gov.defra.mmocatchrecord.core.root.MainActivity
import uk.gov.defra.mmocatchrecord.core.root.RootScreenTestTags

/**
 * Instrumented smoke test: on first launch (no session), the app shows the sign-in placeholder and
 * exposes the WCAG 2.2 AA semantics (heading, labelled/clickable primary action) that TalkBack and
 * other assistive technologies rely on. Driven by stable `testTag`s, not display text, since several
 * placeholder screens share action label text (e.g. "Sign in").
 */
@RunWith(AndroidJUnit4::class)
class LaunchSmokeTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launcherShowsSignInPlaceholder() {
        composeTestRule.onNodeWithTag(RootScreenTestTags.SIGN_IN_SCREEN).assertExists()
    }

    @Test
    fun signInTitleIsExposedAsAnAccessibilityHeading() {
        composeTestRule
            .onNode(SemanticsMatcher.expectValue(SemanticsProperties.Heading, Unit))
            .assertExists()
            .assertIsDisplayed()
    }

    @Test
    fun signInPrimaryActionIsReachableAndClickable() {
        composeTestRule
            .onNodeWithTag(RootScreenTestTags.SIGN_IN_ACTION)
            .assertExists()
            .assertIsDisplayed()
            .assertHasClickAction()
    }
}
