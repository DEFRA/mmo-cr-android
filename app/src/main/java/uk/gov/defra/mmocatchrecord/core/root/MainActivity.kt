package uk.gov.defra.mmocatchrecord.core.root

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme

/**
 * Single-activity host for the app. All navigation happens within Compose via [RootNavigation].
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // Main-thread only (read by the platform inside setKeepOnScreenCondition's poll, written once by
    // RootNavigation's first-frame callback below) — see the field's use for why this exists.
    private var isFirstComposeFrameRendered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install the GOV.UK-blue cold-start splash background (AndroidX SplashScreen API) before the
        // first frame. It shows no logo/icon of its own (see Theme.MMOCatchRecord.Starting in
        // res/values/themes.xml) — the wordmark + crown are drawn exactly once, at full size, by the
        // in-app SplashScreen composable (core.root.SplashScreen) that composes underneath it.
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Root-cause fix for the wordmark/crown never appearing on some cold starts: keep the *system*
        // splash on screen until Compose has actually rendered its first real frame, instead of letting
        // the platform dismiss it as soon as it merely detects the window is "drawn" (confirmed by
        // instrumented testing to happen while the Activity window itself still had NO_SURFACE/no frame —
        // a cold, JIT-cold Hilt + Compose start can take several hundred ms to a second before the window
        // gets its first real surface). Without this gate, [RootNavigation]'s own minimum-visible-duration
        // floor for [SplashScreen] — timed from Compose *composition*, which happens well before that
        // first real frame — had already elapsed by the time anything was actually painted on screen, so
        // the resolved phase (sign-in/app-lock/home) was already showing on the very first visible frame
        // and the branded splash was silently skipped. Gating the system splash's release on
        // isFirstComposeFrameRendered guarantees the in-app SplashScreen is the first thing ever painted.
        splashScreen.setKeepOnScreenCondition { !isFirstComposeFrameRendered }

        // The system splash's default exit transition is a ~200ms fade-out of its own (blue,
        // logo-less) view, played on top of the Activity content already drawing underneath. Removing
        // it immediately instead of letting that animation play avoids any residual flash/flicker during
        // the handover to the in-app SplashScreen composable, which is already the correct frame by the
        // time this listener fires.
        splashScreen.setOnExitAnimationListener { splashScreenView -> splashScreenView.remove() }

        setContent {
            MmoTheme {
                RootNavigation(
                    sessionCoordinator = hiltViewModel(),
                    onFirstFrameRendered = { isFirstComposeFrameRendered = true },
                )
            }
        }
    }
}
