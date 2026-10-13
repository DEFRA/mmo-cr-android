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

/** Exercises rememberWizardScaffoldState's real Hilt branch while bypassing Room-backed navigation.
 * MainActivity is used only as a Hilt host so Robolectric never loads SQLCipher. */
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
