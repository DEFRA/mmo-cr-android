package uk.gov.defra.mmocatchrecord.core.root

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.gov.defra.mmocatchrecord.feature.signin.presentation.SignInScreenTestTags

/** Launches the real Hilt-backed MainActivity only as far as sign-in.
 * Stopping there avoids Room/SQLCipher paths unavailable under Robolectric's host JVM. */
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
