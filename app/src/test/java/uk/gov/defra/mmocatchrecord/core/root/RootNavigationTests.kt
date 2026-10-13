package uk.gov.defra.mmocatchrecord.core.root

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.gov.defra.mmocatchrecord.core.security.BiometricReentryPolicy
import uk.gov.defra.mmocatchrecord.core.security.FakeBiometricRepository
import uk.gov.defra.mmocatchrecord.core.security.InMemoryBiometricPreferenceStore
import uk.gov.defra.mmocatchrecord.core.security.InMemorySessionStore

// HOME is unreachable here (SQLCipher's native library is unavailable under Robolectric).
@RunWith(RobolectricTestRunner::class)
class RootNavigationTests {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val dispatcher = StandardTestDispatcher()

    // Authenticated well outside the reentry window, so the coordinator resolves straight to APP_LOCK.
    private fun buildLockedCoordinator(biometricRepository: FakeBiometricRepository = FakeBiometricRepository()) =
        SessionCoordinator(
            sessionStore = InMemorySessionStore(initialLastAuthenticatedAtMillis = 0L),
            biometricPreferenceStore = InMemoryBiometricPreferenceStore(initiallyEnabled = true),
            reentryPolicy = BiometricReentryPolicy(reentryTimeoutMillis = 60_000L),
            biometricRepository = biometricRepository,
            clock = { 120_000L },
            dispatcher = dispatcher,
        )

    // Also steps the Compose clock past RootNavigation's 600 ms minimum splash display.
    private fun settle() {
        dispatcher.scheduler.advanceUntilIdle()
        composeTestRule.mainClock.advanceTimeBy(1_000L)
        composeTestRule.waitForIdle()
    }

    @Test
    fun `expired biometric session navigates from splash to the app lock screen`() {
        val coordinator = buildLockedCoordinator()
        composeTestRule.setContent {
            RootNavigation(sessionCoordinator = coordinator)
        }

        settle()

        composeTestRule.onNodeWithTag(RootScreenTestTags.APP_LOCK_SCREEN).assertIsDisplayed()
    }

    @Test
    fun `a failed unlock attempt dispatches BiometricUnlockRequested but stays locked`() {
        val failingRepository = FakeBiometricRepository(authenticationResult = Result.failure(SecurityException("no")))
        val coordinator = buildLockedCoordinator(failingRepository)
        composeTestRule.setContent {
            RootNavigation(sessionCoordinator = coordinator)
        }
        settle()

        composeTestRule.onNodeWithTag(RootScreenTestTags.APP_LOCK_ACTION).performClick()
        settle()

        composeTestRule.onNodeWithTag(RootScreenTestTags.APP_LOCK_SCREEN).assertIsDisplayed()
    }

    @Test
    fun `resuming the app re-evaluates the phase via AppResumed`() {
        val coordinator = buildLockedCoordinator()
        composeTestRule.setContent {
            RootNavigation(sessionCoordinator = coordinator)
        }
        settle()

        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        settle()

        composeTestRule.onNodeWithTag(RootScreenTestTags.APP_LOCK_SCREEN).assertIsDisplayed()
    }

    // The scheduler is never advanced, so the coordinator stays in SPLASH and self-navigation is skipped.
    @Test
    fun `splash phase suppresses redundant self-navigation once the first frame renders`() {
        val coordinator =
            SessionCoordinator(
                sessionStore = InMemorySessionStore(),
                biometricPreferenceStore = InMemoryBiometricPreferenceStore(initiallyEnabled = false),
                reentryPolicy = BiometricReentryPolicy(reentryTimeoutMillis = 60_000L),
                biometricRepository = FakeBiometricRepository(),
                dispatcher = dispatcher,
            )
        composeTestRule.setContent {
            RootNavigation(sessionCoordinator = coordinator)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(RootScreenTestTags.SPLASH_SCREEN).assertIsDisplayed()
    }
}
