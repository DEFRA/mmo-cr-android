package uk.gov.defra.mmocatchrecord.core.root

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.gov.defra.mmocatchrecord.feature.signin.presentation.SignInScreenTestTags

/**
 * JVM/Robolectric port of the androidTest `LaunchSmokeTest`: launches the real, Hilt-backed
 * [MainActivity] and waits for the real sign-in screen to appear, exercising [MainActivity]'s
 * `onCreate` (system splash gating, edge-to-edge, [RootNavigation] wiring) together with
 * [RootNavigation]'s/[uk.gov.defra.mmocatchrecord.common.navigation.MmoNavHost]'s SPLASH -> SIGN_IN
 * transition on a fresh (no prior session) install. Reaching `signin_screen` alone is sufficient —
 * this test never submits credentials, since the Home destination and the nested catch-record wizard
 * graph both reach Room-backed (SQLCipher) repositories via `hiltViewModel()`, and SQLCipher's native
 * library is unavailable under Robolectric's host JVM (see other test files' documentation of this
 * constraint for the same reason).
 */
@RunWith(RobolectricTestRunner::class)
class MainActivityNavigationTests {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `fresh install resolves splash to the real sign in screen`() {
        composeTestRule.waitUntil(timeoutMillis = SIGN_IN_TIMEOUT_MILLIS) {
            composeTestRule
                .onAllNodesWithTag(SignInScreenTestTags.SCREEN)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeTestRule.onNodeWithTag(SignInScreenTestTags.SCREEN).assertIsDisplayed()
    }

    private companion object {
        const val SIGN_IN_TIMEOUT_MILLIS = 10_000L
    }
}
