package uk.gov.defra.mmocatchrecord

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.gov.defra.mmocatchrecord.core.root.MainActivity
import uk.gov.defra.mmocatchrecord.feature.signin.presentation.SignInScreenTestTags

/**
 * Instrumented smoke test: on first launch (no session), the app shows the real sign-in screen and
 * exposes the WCAG 2.2 AA semantics (heading, labelled/clickable primary action) that TalkBack and
 * other assistive technologies rely on. Driven by stable `testTag`s, not display text, since several
 * screens share action label text (e.g. "Sign in").
 *
 * Asserts against [SignInScreenTestTags] (the tags actually rendered by
 * `feature.signin.presentation.SignInScreen`, which [uk.gov.defra.mmocatchrecord.common.navigation.MmoNavHost]
 * wires in for [uk.gov.defra.mmocatchrecord.common.navigation.SignInRoute]) rather than the
 * Stage-1 `core.root.RootScreenTestTags` placeholder tags, which stopped matching any composed screen
 * once the real sign-in feature replaced the placeholder.
 */
@RunWith(AndroidJUnit4::class)
class LaunchSmokeTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    /**
     * The in-app splash deliberately stays up for a minimum wall-clock duration (see `RootNavigation`'s
     * `SPLASH_MINIMUM_VISIBLE_DURATION_MILLIS`, timed with `SystemClock`, not the Compose test clock) before
     * navigating to the resolved phase, so wait for the sign-in screen rather than asserting immediately.
     */
    @Before
    fun waitForSignInScreen() {
        composeTestRule.waitUntil(timeoutMillis = SIGN_IN_TIMEOUT_MILLIS) {
            composeTestRule
                .onAllNodesWithTag(SignInScreenTestTags.SCREEN)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun launcherShowsSignInPlaceholder() {
        composeTestRule.onNodeWithTag(SignInScreenTestTags.SCREEN).assertExists()
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
            .onNodeWithTag(SignInScreenTestTags.SUBMIT_ACTION)
            .assertExists()
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    private companion object {
        const val SIGN_IN_TIMEOUT_MILLIS = 10_000L
    }
}
