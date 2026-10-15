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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

// Pixel assertions need native graphics; legacy Robolectric capture is fully transparent.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class CommonComponentsTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `PrimaryActionButton fills with GOV UK success green when enabled`() {
        composeTestRule.setContent {
            MmoTheme {
                PrimaryActionButton(text = "Continue", onClick = {}, modifier = Modifier.testTag("primary"))
            }
        }

        val image = composeTestRule.onNodeWithTag("primary").captureToImage()
        assertTrue(image.countOf(MmoColors.SuccessGreen) > image.pixels().size / 2)
        assertEquals(0, image.countOf(MmoColors.Grey2))
    }

    @Test
    fun `PrimaryActionButton fills with the disabled grey when disabled`() {
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

        val image = composeTestRule.onNodeWithTag("primary").captureToImage()
        assertTrue(image.countOf(MmoColors.Grey2) > image.pixels().size / 2)
        assertEquals(0, image.countOf(MmoColors.SuccessGreen))
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
        assertTrue(composeTestRule.onNodeWithTag("expandable").captureToImage().countOf(MmoColors.Grey2) > 0)
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
    fun `MmoBottomNavigationBar routes the home tab click`() {
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

        composeTestRule.onNodeWithTag(MmoBottomNavigationBarTestTags.HOME_TAB).performClick()

        assertEquals(0, clickedIndex)
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
        assertEquals(1, clickedIndex)

        composeTestRule.onNodeWithTag(MmoBottomNavigationBarTestTags.SETTINGS_TAB).performClick()
        assertEquals(2, clickedIndex)
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

    @Test
    fun `RoyalCrestFooter renders under its given test tag`() {
        composeTestRule.setContent {
            MmoTheme {
                RoyalCrestFooter(testTag = "royal_crest_footer")
            }
        }

        composeTestRule.onNodeWithTag("royal_crest_footer").assertIsDisplayed()
    }
}
