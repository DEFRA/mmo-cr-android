package uk.gov.defra.mmocatchrecord.feature.signin.data

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.gov.defra.mmocatchrecord.BuildConfig
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.core.android.CurrentActivityProvider
import uk.gov.defra.mmocatchrecord.core.root.MainActivity
import java.lang.ref.WeakReference

/**
 * Device-only (no credentials): opens the real Custom Tab for the browser web-fallback leg (ADR 0019
 * Phase 2.1c), then presses Back and asserts the cancel path (1e) completes without a crash.
 */
@RunWith(AndroidJUnit4::class)
class MsalNativeAuthClientBrowserFallbackDeviceTest {
    @Before
    fun requiresDevFlavourAndACustomTabsBrowser() {
        assumeTrue("Needs the devDebug msal config with redirect_uri", BuildConfig.FLAVOR == "dev")
        assumeTrue("Needs a browser capable of opening https: links on this device", aBrowserIsAvailable())
    }

    @Test
    fun theUserCancellingTheBrowserTabMapsToACleanCancelledOutcome() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        var client: MsalNativeAuthClient? = null
        // Only capture references here: this block runs on the main thread, and
        // signInInteractively() dispatches back onto it, so it must run off this thread.
        scenario.onActivity { activity ->
            client =
                MsalNativeAuthClient(
                    context = activity.applicationContext,
                    configResId = R.raw.msal_native_auth_config,
                    currentActivityProvider = CurrentActivityProvider { WeakReference(activity).get() },
                )
        }

        val outcome =
            runBlocking {
                // Dismiss concurrently with the call below: the Custom Tab only opens once
                // signInInteractively() runs, so pressing Back first would hit nothing.
                val backPress =
                    launch {
                        delay(CUSTOM_TAB_OPEN_WAIT_MILLIS)
                        InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(
                            android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK,
                        )
                    }
                requireNotNull(client).signInInteractively(listOf(API_SCOPE)).also { backPress.cancel() }
            }

        assertOutcomeIsSafe(outcome)
        scenario.close()
    }

    /** The Back press (1e) must map exactly to [NativeAuthOutcome.BrowserCancelled] — never loosened. */
    private fun assertOutcomeIsSafe(outcome: NativeAuthOutcome) {
        assertTrue("unexpected outcome: $outcome", outcome is NativeAuthOutcome.BrowserCancelled)
    }

    private fun aBrowserIsAvailable(): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
        val packageManager = InstrumentationRegistry.getInstrumentation().targetContext.packageManager
        return intent.resolveActivity(packageManager) != null
    }

    private companion object {
        const val API_SCOPE = "api://8bc065bf-046f-4d18-834a-753b325e5221/access_as_user"
        const val CUSTOM_TAB_OPEN_WAIT_MILLIS = 12_000L
    }
}
