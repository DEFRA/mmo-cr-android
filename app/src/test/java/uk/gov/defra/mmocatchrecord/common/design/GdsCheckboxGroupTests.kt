package uk.gov.defra.mmocatchrecord.common.design

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GdsCheckboxGroupTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `secondary text is displayed under a checked option when the default conditional content is used`() {
        composeTestRule.setContent {
            GdsCheckboxGroup(
                options = listOf(GdsCheckboxOption(id = "a", label = "Seine nets", secondaryText = "50mm mesh")),
                checkedOptionIds = setOf("a"),
                onCheckedChange = { _, _ -> },
            )
        }

        composeTestRule.onNodeWithText("Seine nets").assertIsDisplayed()
        composeTestRule.onNodeWithText("50mm mesh").assertIsDisplayed()
    }
}
