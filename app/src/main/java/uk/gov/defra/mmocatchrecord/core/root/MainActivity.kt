package uk.gov.defra.mmocatchrecord.core.root

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.koin.androidx.compose.koinViewModel
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme

/**
 * Single-activity host for the app. All navigation happens within Compose via [RootNavigation].
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MmoTheme {
                RootNavigation(sessionCoordinator = koinViewModel())
            }
        }
    }
}
