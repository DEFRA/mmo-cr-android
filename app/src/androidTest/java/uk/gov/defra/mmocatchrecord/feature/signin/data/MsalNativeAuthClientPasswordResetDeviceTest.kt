package uk.gov.defra.mmocatchrecord.feature.signin.data

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uk.gov.defra.mmocatchrecord.BuildConfig
import uk.gov.defra.mmocatchrecord.core.android.CurrentActivityProvider
import uk.gov.defra.mmocatchrecord.core.root.MainActivity
import java.lang.ref.WeakReference

/**
 * Manual spike for ADR 0027: starts a reset for instrumentation arg `resetProbeEmail`; skipped if absent.
 */
@RunWith(AndroidJUnit4::class)
class MsalNativeAuthClientPasswordResetDeviceTest {
    private var probeEmail: String? = null

    @Before
    fun requiresADebugBuildAndAProbeAddress() {
        assumeTrue("Needs a debug build for the fixture's redirect_uri", BuildConfig.DEBUG)
        probeEmail =
            InstrumentationRegistry.getArguments().getString(PROBE_EMAIL_ARGUMENT)?.takeIf { it.isNotBlank() }
        assumeTrue("Pass $PROBE_EMAIL_ARGUMENT to run this DEV-tenant spike", probeEmail != null)
    }

    @Test
    fun startingAResetReturnsACodeRequiredOutcomeWhetherOrNotTheAccountExists() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        var client: MsalNativeAuthClient? = null
        scenario.onActivity { activity ->
            client =
                MsalNativeAuthClient(
                    context = activity.applicationContext,
                    configJson = { devDebugConfigJson() },
                    currentActivityProvider = CurrentActivityProvider { WeakReference(activity).get() },
                )
        }

        val outcome = runBlocking { requireNotNull(client).startPasswordReset(requireNotNull(probeEmail)) }

        // Never print the outcome's contents: a real account would otherwise be disclosed in the log.
        assertTrue("start did not return CodeRequired (AC3)", outcome is NativeResetOutcome.CodeRequired)
        scenario.close()
    }

    private fun devDebugConfigJson(): String =
        InstrumentationRegistry
            .getInstrumentation()
            .context
            .assets
            .open(DEV_DEBUG_CONFIG_ASSET)
            .bufferedReader()
            .use { it.readText() }

    private companion object {
        const val PROBE_EMAIL_ARGUMENT = "resetProbeEmail"
        const val DEV_DEBUG_CONFIG_ASSET = "msal_native_auth_config_dev_debug.json"
    }
}
