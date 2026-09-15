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
        // Install the GOV.UK-branded cold-start splash (AndroidX SplashScreen API) before the first
        // frame. It is dismissed automatically once Compose draws — no artificial delay.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MmoTheme {
                RootNavigation(sessionCoordinator = hiltViewModel())
            }
        }
    }
}
