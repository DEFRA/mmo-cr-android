package uk.gov.defra.mmocatchrecord.common.design

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GdsDateInputTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `renders with default empty value, no focus requesters, and shows field errors`() {
        composeTestRule.setContent {
            GdsDateInput(
                value = GdsDateInputValue(),
                onValueChange = {},
                dayTestTag = "day",
                monthTestTag = "month",
                yearTestTag = "year",
                dayError = "Enter a day",
                monthError = "Enter a month",
                yearError = "Enter a year",
            )
        }

        composeTestRule.onNodeWithTag("day").assertIsDisplayed()
        composeTestRule.onNodeWithText("Enter a day").assertIsDisplayed()
        composeTestRule.onNodeWithText("Enter a month").assertIsDisplayed()
        composeTestRule.onNodeWithText("Enter a year").assertIsDisplayed()
    }
}
