package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow

import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.gov.defra.mmocatchrecord.core.root.MainActivity

/**
 * Exercises [rememberWizardScaffoldState]'s real, Hilt-backed branch - the one path every other
 * `CatchRecordWizardScaffold` test avoids by providing [LocalWizardScaffoldState] (via [WizardTestTheme])
 * instead. Reuses the real [MainActivity] purely as a Hilt host and replaces its content directly
 * (bypassing `RootNavigation`/`MmoNavHost`, so no Room/SQLCipher access is ever triggered) - the
 * `AppLanguageViewModel`/`ConnectivityViewModel` this resolves are DataStore/connectivity backed only.
 */
@RunWith(RobolectricTestRunner::class)
class CatchRecordWizardScaffoldHiltTests {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `resolves its scaffold state from the real hilt backed view models`() {
        composeTestRule.activity.setContent {
            CatchRecordWizardScaffold(
                screenTestTag = "hilt_scaffold",
                title = "Title",
                onBack = {},
            ) {
                Text("content")
            }
        }

        composeTestRule.onNodeWithTag("hilt_scaffold").assertIsDisplayed()
    }
}
