package uk.gov.defra.mmocatchrecord.common.design

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import uk.gov.defra.mmocatchrecord.R

class OfflineBannerTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun offlineBannerDisplaysLabelAndMessage() {
        composeTestRule.setContent {
            MmoTheme {
                OfflineBanner()
            }
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        composeTestRule.onNodeWithTag("offline_banner").assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.offline_banner_label))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.offline_banner_message))
            .assertIsDisplayed()
    }
}
