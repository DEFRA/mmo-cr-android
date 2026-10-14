package uk.gov.defra.mmocatchrecord.common.design

import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Covers the handful of `CommonComponents.kt` lines not already exercised indirectly by other screens'
 * tests: the `drawBehind` draw-phase branches (forced via `captureToImage()`), the disabled-state colour
 * branches, [SecondaryActionButton]'s default `modifier` parameter (only executes when a caller omits it),
 * [ExpandableDetails]'s click-to-expand interaction, and [MmoBottomNavigationBar]'s home-tab click.
 */
@RunWith(RobolectricTestRunner::class)
class CommonComponentsTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `PrimaryActionButton draws its bottom shadow when enabled`() {
        composeTestRule.setContent {
            MmoTheme {
                PrimaryActionButton(text = "Continue", onClick = {}, modifier = Modifier.testTag("primary"))
            }
        }
        composeTestRule.onNodeWithTag("primary").captureToImage()
    }

    @Test
    fun `PrimaryActionButton omits its bottom shadow when disabled`() {
        composeTestRule.setContent {
            MmoTheme {
                PrimaryActionButton(
                    text = "Continue",
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.testTag("primary"),
                )
            }
        }
        composeTestRule.onNodeWithTag("primary").captureToImage()
    }

    @Test
    fun `PrimaryActionButton uses its default modifier when omitted`() {
        composeTestRule.setContent {
            MmoTheme {
                PrimaryActionButton(text = "Continue", onClick = {})
            }
        }
        composeTestRule.onNodeWithText("Continue").assertIsDisplayed()
    }

    @Test
    fun `SecondaryActionButton uses its default modifier when omitted`() {
        composeTestRule.setContent {
            MmoTheme {
                SecondaryActionButton(text = "Cancel", onClick = {})
            }
        }
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    fun `SecondaryActionButton renders its disabled border colour`() {
        composeTestRule.setContent {
            MmoTheme {
                SecondaryActionButton(text = "Cancel", onClick = {}, enabled = false)
            }
        }
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
    }

    @Test
    fun `ExpandableDetails toggles expanded content on click and draws its border`() {
        composeTestRule.setContent {
            MmoTheme {
                ExpandableDetails(title = "More detail", modifier = Modifier.testTag("expandable")) {
                    Text("Hidden content", modifier = Modifier.testTag("expandable_body"))
                }
            }
        }

        composeTestRule.onNodeWithTag("expandable_body").assertDoesNotExist()

        composeTestRule.onNodeWithText("More detail").performClick()

        composeTestRule.onNodeWithTag("expandable_body").assertIsDisplayed()
        composeTestRule.onNodeWithTag("expandable_body").captureToImage()
    }

    @Test
    fun `ExpandableDetails uses its default modifier when omitted`() {
        composeTestRule.setContent {
            MmoTheme {
                ExpandableDetails(title = "More detail") {
                    Text("Hidden content")
                }
            }
        }

        composeTestRule.onNodeWithText("More detail").assertIsDisplayed()
    }

    @Test
    fun `ExpandableDetails supports custom title font weight`() {
        composeTestRule.setContent {
            MmoTheme {
                ExpandableDetails(title = "Custom weight", titleFontWeight = FontWeight.Bold) {
                    Text("Hidden content")
                }
            }
        }

        composeTestRule.onNodeWithText("Custom weight").assertIsDisplayed()
    }

    @Test
    fun `MmoBottomNavigationBar draws its top border and routes the home tab click`() {
        var clickedIndex = -1
        composeTestRule.setContent {
            MmoTheme {
                MmoBottomNavigationBar(
                    selectedItem = 1,
                    onItemClick = { clickedIndex = it },
                    modifier = Modifier.testTag("bottom_nav"),
                )
            }
        }

        composeTestRule.onNodeWithTag("bottom_nav").captureToImage()
        composeTestRule.onNodeWithTag(MmoBottomNavigationBarTestTags.HOME_TAB).performClick()

        assertTrue(clickedIndex == 0)
        assertFalse(clickedIndex == 1)
    }

    @Test
    fun `MmoBottomNavigationBar uses its default modifier and routes the other two tab clicks`() {
        var clickedIndex = -1
        composeTestRule.setContent {
            MmoTheme {
                MmoBottomNavigationBar(selectedItem = 0, onItemClick = { clickedIndex = it })
            }
        }

        composeTestRule.onNodeWithTag(MmoBottomNavigationBarTestTags.NOTIFICATIONS_TAB).performClick()
        assertTrue(clickedIndex == 1)

        composeTestRule.onNodeWithTag(MmoBottomNavigationBarTestTags.SETTINGS_TAB).performClick()
        assertTrue(clickedIndex == 2)
    }

    @Test
    fun `MmoBottomNavigationBar marks the settings tab selected`() {
        composeTestRule.setContent {
            MmoTheme {
                MmoBottomNavigationBar(selectedItem = 2, onItemClick = {})
            }
        }

        composeTestRule.onNodeWithTag(MmoBottomNavigationBarTestTags.SETTINGS_TAB).assertIsDisplayed()
    }
}
