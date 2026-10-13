package uk.gov.defra.mmocatchrecord.common.design

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.requestFocus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Exercises [GdsStatisticalRectangleGrid]'s row-wrapping (`chunked`), partial-final-row padding, click
 * selection and focus-indicator branches. `captureToImage()` forces Robolectric to actually run the
 * `Modifier.border`/`background` draw phase - see `MmoIconsTests`/`MapCanvasTests` for the pattern.
 */
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

        composeTestRule.onNodeWithTag("${prefix}_0").captureToImage()
        composeTestRule.onNodeWithTag("${prefix}_1").captureToImage()
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
    fun `focusing a cell renders the focus indicator`() {
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

        composeTestRule.onNodeWithTag("${prefix}_0").requestFocus()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("${prefix}_0").captureToImage()
    }
}
