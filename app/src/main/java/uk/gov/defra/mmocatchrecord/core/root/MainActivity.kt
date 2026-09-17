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
    override fun onCreate(savedInstanceState: Bundle?) {
        // Install the GOV.UK-blue cold-start splash background (AndroidX SplashScreen API) before the
        // first frame. It shows no logo/icon of its own (see Theme.MMOCatchRecord.Starting in
        // res/values/themes.xml) — the wordmark + crown are drawn exactly once, at full size, by the
        // in-app SplashScreen composable (core.root.SplashScreen) that composes underneath it.
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // The system splash's default exit transition is a ~200ms fade-out of its own (blue,
        // logo-less) view, played on top of the Activity content already drawing underneath. Removing
        // it immediately instead of letting that animation play avoids any residual flash/flicker during
        // the handover to the in-app SplashScreen composable, which is already the correct frame by the
        // time this listener fires.
        splashScreen.setOnExitAnimationListener { splashScreenView -> splashScreenView.remove() }

        setContent {
            MmoTheme {
                RootNavigation(sessionCoordinator = hiltViewModel())
            }
        }
    }
}
