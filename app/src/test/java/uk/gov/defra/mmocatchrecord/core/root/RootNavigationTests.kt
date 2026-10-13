package uk.gov.defra.mmocatchrecord.core.root

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.gov.defra.mmocatchrecord.core.security.BiometricReentryPolicy
import uk.gov.defra.mmocatchrecord.core.security.FakeBiometricRepository
import uk.gov.defra.mmocatchrecord.core.security.InMemoryBiometricPreferenceStore
import uk.gov.defra.mmocatchrecord.core.security.InMemorySessionStore

/**
 * [RootNavigation] driven by a fakes-backed [SessionCoordinator] (no Hilt needed - `sessionCoordinator`
 * is a plain constructor parameter), covering the APP_LOCK destination and the `AppResumed`
 * lifecycle re-dispatch. The real-Hilt SPLASH -> SIGN_IN path is covered separately by
 * [MainActivityNavigationTests]; the HOME destination is not reachable under Robolectric (SQLCipher's
 * native library is unavailable in this host JVM - see that file's doc comment).
 */
@RunWith(RobolectricTestRunner::class)
class RootNavigationTests {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    /** A session already authenticated well outside the reentry window, so [SessionCoordinator]
     * resolves directly to [RootPhase.APP_LOCK] without ever visiting HOME. */
    private fun buildLockedCoordinator(biometricRepository: FakeBiometricRepository = FakeBiometricRepository()) =
        SessionCoordinator(
            sessionStore = InMemorySessionStore(initialLastAuthenticatedAtMillis = 0L),
            biometricPreferenceStore = InMemoryBiometricPreferenceStore(initiallyEnabled = true),
            reentryPolicy = BiometricReentryPolicy(reentryTimeoutMillis = 60_000L),
            biometricRepository = biometricRepository,
            clock = { 120_000L },
            dispatcher = Dispatchers.Default,
        )

    private fun waitForTag(tag: String) {
        composeTestRule.waitUntil(timeoutMillis = 10_000) {
            composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `expired biometric session navigates from splash to the app lock screen`() {
        val coordinator = buildLockedCoordinator()
        composeTestRule.setContent {
            RootNavigation(sessionCoordinator = coordinator)
        }

        waitForTag(RootScreenTestTags.APP_LOCK_SCREEN)

        composeTestRule.onNodeWithTag(RootScreenTestTags.APP_LOCK_SCREEN).assertIsDisplayed()
    }

    @Test
    fun `a failed unlock attempt dispatches BiometricUnlockRequested but stays locked`() {
        val failingRepository = FakeBiometricRepository(authenticationResult = Result.failure(SecurityException("no")))
        val coordinator = buildLockedCoordinator(failingRepository)
        composeTestRule.setContent {
            RootNavigation(sessionCoordinator = coordinator)
        }
        waitForTag(RootScreenTestTags.APP_LOCK_SCREEN)

        composeTestRule.onNodeWithTag(RootScreenTestTags.APP_LOCK_ACTION).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(RootScreenTestTags.APP_LOCK_SCREEN).assertIsDisplayed()
    }

    @Test
    fun `resuming the app re-evaluates the phase via AppResumed`() {
        val coordinator = buildLockedCoordinator()
        composeTestRule.setContent {
            RootNavigation(sessionCoordinator = coordinator)
        }
        waitForTag(RootScreenTestTags.APP_LOCK_SCREEN)

        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(RootScreenTestTags.APP_LOCK_SCREEN).assertIsDisplayed()
    }

    /** A dispatcher whose scheduler is never advanced leaves the coordinator parked at its initial
     * SPLASH phase forever - exercising the self-navigation guard once the first frame has drawn. */
    @Test
    fun `splash phase suppresses redundant self-navigation once the first frame renders`() {
        val coordinator =
            SessionCoordinator(
                sessionStore = InMemorySessionStore(),
                biometricPreferenceStore = InMemoryBiometricPreferenceStore(initiallyEnabled = false),
                reentryPolicy = BiometricReentryPolicy(reentryTimeoutMillis = 60_000L),
                biometricRepository = FakeBiometricRepository(),
                dispatcher = StandardTestDispatcher(),
            )
        composeTestRule.setContent {
            RootNavigation(sessionCoordinator = coordinator)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(RootScreenTestTags.SPLASH_SCREEN).assertIsDisplayed()
    }
}
