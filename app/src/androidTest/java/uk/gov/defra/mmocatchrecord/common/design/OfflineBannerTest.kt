package uk.gov.defra.mmocatchrecord.common.design

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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

        composeTestRule.onNodeWithTag("offline_banner").assertIsDisplayed()
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.offline_banner_label))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.offline_banner_message))
            .assertIsDisplayed()
    }
}
