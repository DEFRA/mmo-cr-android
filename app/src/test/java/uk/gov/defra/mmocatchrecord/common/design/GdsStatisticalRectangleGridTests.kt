package uk.gov.defra.mmocatchrecord.common.design

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.requestFocus
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
class GdsStatisticalRectangleGridTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val prefix = "rect_cell"

    @Test
    fun `wraps codes into rows of columns and pads a partial final row`() {
        var selected: String? = null
        composeTestRule.setContent {
            MmoTheme {
                GdsStatisticalRectangleGrid(
                    codes = listOf("38E9", "39E9", "40E9", "41E9"),
                    selectedCode = selected,
                    onCodeSelected = { selected = it },
                    cellTestTagPrefix = prefix,
                    columns = 3,
                )
            }
        }

        // Row 2 has only one real code (index 3) plus two padding spacers - all four cells render.
        composeTestRule.onNodeWithTag("${prefix}_0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("${prefix}_3").assertIsDisplayed()
    }

    @Test
    fun `clicking a cell reports its code as selected`() {
        var selected: String? = null
        composeTestRule.setContent {
            MmoTheme {
                GdsStatisticalRectangleGrid(
                    codes = listOf("38E9", "39E9"),
                    selectedCode = selected,
                    onCodeSelected = { selected = it },
                    cellTestTagPrefix = prefix,
                )
            }
        }

        composeTestRule.onNodeWithTag("${prefix}_1").performClick()

        assertEquals("39E9", selected)
    }

    @Test
    fun `selected cell renders with the selected border and background`() {
        composeTestRule.setContent {
            MmoTheme {
                GdsStatisticalRectangleGrid(
                    codes = listOf("38E9", "39E9"),
                    selectedCode = "38E9",
                    onCodeSelected = {},
                    cellTestTagPrefix = prefix,
                )
            }
        }

        val selected = composeTestRule.onNodeWithTag("${prefix}_0").captureToImage()
        assertTrue(selected.countOf(MmoColors.GovBlue) > 0)
        assertTrue(selected.countOf(MmoColors.SelectedTint) > 0)
        assertEquals(0, selected.countOf(MmoColors.White))
        val unselected = composeTestRule.onNodeWithTag("${prefix}_1").captureToImage()
        assertTrue(unselected.countOf(MmoColors.Grey2) > 0)
        assertTrue(unselected.countOf(MmoColors.White) > 0)
        assertEquals(0, unselected.countOf(MmoColors.GovBlue))
    }

    @Test
    fun `omitting the cell test tag prefix defaults to blank and skips the test tag modifier`() {
        composeTestRule.setContent {
            MmoTheme {
                GdsStatisticalRectangleGrid(
                    codes = listOf("38E9", "39E9"),
                    selectedCode = null,
                    onCodeSelected = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("${prefix}_0").assertDoesNotExist()
    }

    @Test
    fun `focusing a cell shows the GOV UK yellow focus indicator only on that cell`() {
        composeTestRule.setContent {
            MmoTheme {
                GdsStatisticalRectangleGrid(
                    codes = listOf("38E9", "39E9"),
                    selectedCode = null,
                    onCodeSelected = {},
                    cellTestTagPrefix = prefix,
                )
            }
        }

        assertEquals(0, composeTestRule.onNodeWithTag("${prefix}_0").captureToImage().countOf(MmoColors.FocusYellow))

        composeTestRule.onNodeWithTag("${prefix}_0").requestFocus()
        composeTestRule.waitForIdle()

        assertTrue(composeTestRule.onNodeWithTag("${prefix}_0").captureToImage().countOf(MmoColors.FocusYellow) > 0)
        assertEquals(0, composeTestRule.onNodeWithTag("${prefix}_1").captureToImage().countOf(MmoColors.FocusYellow))
    }
}
