package uk.gov.defra.mmocatchrecord.common.design

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.core.language.AppLanguage

@RunWith(RobolectricTestRunner::class)
class AppLanguageProviderTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `stringResource resolves welsh then english as the language toggles`() {
        var language by mutableStateOf(AppLanguage.WELSH)
        composeTestRule.setContent {
            AppLanguageProvider(language = language) {
                Text(stringResource(R.string.vessel_selection_title))
            }
        }

        composeTestRule.onNodeWithText("Pa long a ddefnyddiwyd gennych?").assertIsDisplayed()

        language = AppLanguage.ENGLISH
        composeTestRule.onNodeWithText("Which vessel did you use?").assertIsDisplayed()
    }
}
