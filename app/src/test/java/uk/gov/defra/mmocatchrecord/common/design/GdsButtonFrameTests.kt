package uk.gov.defra.mmocatchrecord.common.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.requestFocus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// mdpi makes 1dp exactly 1px, so drawn rows can be counted directly.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "mdpi")
@RunWith(RobolectricTestRunner::class)
class GdsButtonFrameTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var inputMode: InputModeManager

    private fun render(
        tag: String = TAG,
        content: @Composable () -> Unit,
    ): ImageBitmap {
        composeTestRule.setContent {
            inputMode = LocalInputModeManager.current
            MmoTheme { content() }
        }
        val button = composeTestRule.onNodeWithTag(tag)
        button.assertHeightIsEqualTo(Spacing.minTouchTarget)
        return button.captureToImage()
    }

    // Rows of a text-free column near the left edge that are drawn in [color].
    private fun ImageBitmap.rowsOf(color: Color): List<Int> {
        val map = toPixelMap()
        return (0 until map.height).filter { map[EDGE_COLUMN, it] == color }
    }

    @Test
    fun `primary button draws a 40dp green box with its shadow inside the 48dp target`() {
        val image = render { PrimaryActionButton("Continue", onClick = {}, modifier = Modifier.testTag(TAG)) }
        assertEquals((4..41).toList(), image.rowsOf(MmoColors.SuccessGreen))
        assertEquals(listOf(42, 43), image.rowsOf(Color(0xFF005A30)))
    }

    @Test
    fun `secondary button draws a 40dp box with a 1dp border`() {
        val image = render { SecondaryActionButton("Cancel", onClick = {}, modifier = Modifier.testTag(TAG)) }
        assertEquals(listOf(4, 43), image.rowsOf(MmoColors.Text))
    }

    @Test
    fun `offline notice action draws a 40dp white box with a 2dp black edge`() {
        val image =
            render(tag = "notice_action") {
                GdsOfflineNotice(actionLabel = "Try again", onAction = {}, testTag = "notice")
            }
        assertEquals((4..41).toList(), image.rowsOf(MmoColors.White))
        assertEquals(listOf(42, 43), image.rowsOf(MmoColors.Text))
    }

    @Test
    fun `primary button turns focus yellow with a black edge when focused`() {
        render { PrimaryActionButton("Continue", onClick = {}, modifier = Modifier.testTag(TAG)) }
        // Clickables only take focus outside touch mode, as with a keyboard or switch access.
        composeTestRule.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
        composeTestRule.onNodeWithTag(TAG).requestFocus()
        composeTestRule.waitForIdle()

        val focused = composeTestRule.onNodeWithTag(TAG).captureToImage()
        assertEquals((4..41).toList(), focused.rowsOf(MmoColors.FocusYellow))
        assertEquals(listOf(42, 43), focused.rowsOf(MmoColors.Text))
        assertTrue(focused.countOf(MmoColors.SuccessGreen) == 0)
    }

    private companion object {
        const val TAG = "button"
        const val EDGE_COLUMN = 2
    }
}
