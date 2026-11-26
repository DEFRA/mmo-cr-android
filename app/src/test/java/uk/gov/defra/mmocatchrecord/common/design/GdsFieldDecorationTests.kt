package uk.gov.defra.mmocatchrecord.common.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
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

// mdpi makes 1dp exactly 1px, so border rows can be counted directly.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "mdpi")
@RunWith(RobolectricTestRunner::class)
class GdsFieldDecorationTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun render(content: @Composable () -> Unit): ImageBitmap {
        composeTestRule.setContent { MmoTheme { content() } }
        val field = composeTestRule.onNodeWithTag(TAG, useUnmergedTree = true)
        field.assertHeightIsEqualTo(Spacing.minTouchTarget)
        return field.captureToImage()
    }

    // Rows of the centre column drawn in the border colour: one per 1dp edge.
    private fun ImageBitmap.borderRows(): List<Int> {
        val map = toPixelMap()
        return (0 until map.height).filter { map[map.width / 2, it] == MmoColors.Text }
    }

    @Test
    fun `text input draws a 40dp box with a 1dp border inside its 48dp target`() {
        val image = render { GdsTextInput(state = remember { TextFieldState() }, label = "Name", testTag = TAG) }
        assertEquals(listOf(4, 43), image.borderRows())
    }

    @Test
    fun `numeric field draws the same 40dp box with a 1dp border`() {
        val image = render { GdsNumericField(label = "Mesh", value = "", onValueChange = {}, testTag = TAG) }
        assertEquals(listOf(4, 43), image.borderRows())
    }

    @Test
    fun `autocomplete field puts its 40dp box at the bottom so suggestions sit flush`() {
        val image =
            render {
                GdsAutocompleteField(
                    value = "",
                    options = emptyList(),
                    onValueChange = {},
                    onOptionSelected = {},
                    fieldTestTag = TAG,
                    liveRegionTestTag = "live",
                    suggestionTestTagPrefix = "suggestion",
                    noMatchesTestTag = "none",
                )
            }
        assertEquals(listOf(8, 47), image.borderRows())
    }

    @Test
    fun `focus leaves the field free of yellow and its border at 1dp`() {
        render { GdsTextInput(state = remember { TextFieldState() }, label = "Name", testTag = TAG) }
        val field = composeTestRule.onNodeWithTag(TAG, useUnmergedTree = true)
        field.requestFocus()
        composeTestRule.waitForIdle()

        val focused = field.captureToImage()
        assertEquals(0, focused.countOf(MmoColors.FocusYellow))
        assertEquals(listOf(4, 43), focused.borderRows())
    }

    @Test
    fun `a disabled field swaps its black border for grey and fills its box`() {
        val image =
            render {
                GdsTextInput(state = remember { TextFieldState() }, label = "Name", testTag = TAG, enabled = false)
            }
        assertEquals(emptyList<Int>(), image.borderRows())
        assertTrue(image.countOf(MmoColors.Grey1) > 0)
    }

    @Test
    fun `numeric and autocomplete errors are announced with the Error prefix`() {
        composeTestRule.setContent {
            MmoTheme {
                Column {
                    GdsNumericField(label = "Mesh", value = "", onValueChange = {}, testTag = TAG, errorText = "Bad")
                    GdsAutocompleteField(
                        value = "",
                        options = emptyList(),
                        onValueChange = {},
                        onOptionSelected = {},
                        fieldTestTag = "auto",
                        liveRegionTestTag = "live",
                        suggestionTestTagPrefix = "suggestion",
                        noMatchesTestTag = "none",
                        placeholder = "Enter port",
                        errorText = "Pick one",
                    )
                }
            }
        }
        composeTestRule
            .onNodeWithTag(TAG)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "Error: Bad"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Mesh")))
        composeTestRule
            .onNodeWithTag("auto")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "Error: Pick one"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Enter port")))
    }

    private companion object {
        const val TAG = "field"
    }
}
