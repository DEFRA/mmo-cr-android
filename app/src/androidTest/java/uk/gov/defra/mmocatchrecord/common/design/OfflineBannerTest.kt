package uk.gov.defra.mmocatchrecord.common.design

import android.content.Context
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
        val context = ApplicationProvider.getApplicationContext<Context>()
        val label = context.getString(R.string.offline_banner_label)
        val message = context.getString(R.string.offline_banner_message)

        composeTestRule.setContent {
            MmoTheme {
                OfflineBanner()
            }
        }

        composeTestRule.onNodeWithTag("offline_banner").assertIsDisplayed()
        composeTestRule.onNodeWithText(label).assertIsDisplayed()
        composeTestRule.onNodeWithText(message).assertIsDisplayed()
        // TalkBack reads the merged banner node, so it must announce the "Offline" label as well as the message.
        composeTestRule
            .onNodeWithTag("offline_banner")
            .assert(hasText(label))
            .assert(hasText(message))
    }
}
