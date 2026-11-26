package uk.gov.defra.mmocatchrecord.common.design

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Pixel assertions need native graphics; legacy Robolectric capture is fully transparent.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class GdsPasswordInputToggleStyleTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun toggle() = composeTestRule.onNodeWithTag("pw_toggle", useUnmergedTree = true)

    @Test
    fun `toggle is a grey GOV UK secondary button that turns focus yellow when focused`() {
        lateinit var inputMode: InputModeManager
        composeTestRule.setContent {
            inputMode = LocalInputModeManager.current
            MmoTheme {
                GdsPasswordInput(
                    state = remember { TextFieldState("secret") },
                    label = "New password",
                    testTag = "pw",
                    passwordVisible = false,
                    onVisibilityChange = {},
                    labels = GdsPasswordInputLabels("Show password", "Hide password", "shown", "hidden"),
                )
            }
        }

        val unfocused = toggle().captureToImage()
        assertTrue(unfocused.countOf(MmoColors.SecondaryButton) > 0)
        assertTrue(unfocused.countOf(MmoColors.SecondaryButtonShadow) > 0)
        assertEquals(0, unfocused.countOf(MmoColors.FocusYellow))

        // Clickables only take focus outside touch mode, as with a keyboard or switch access.
        composeTestRule.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
        toggle().requestFocus()
        composeTestRule.waitForIdle()

        val focused = toggle().captureToImage()
        assertTrue(focused.countOf(MmoColors.FocusYellow) > 0)
        assertTrue(focused.countOf(MmoColors.Text) > 0)
        assertEquals(0, focused.countOf(MmoColors.SecondaryButton))
    }

    @Test
    fun `field draws a 40dp GOV UK box inside a 48dp touch target with no yellow on focus`() {
        setPasswordInput()
        val field = composeTestRule.onNodeWithTag("pw", useUnmergedTree = true)
        field.assertHeightIsEqualTo(Spacing.minTouchTarget)
        toggle().assertHeightIsEqualTo(Spacing.minTouchTarget)

        field.requestFocus()
        composeTestRule.waitForIdle()

        assertEquals(0, field.captureToImage().countOf(MmoColors.FocusYellow))
    }

    @Test
    fun `a disabled toggle drops its shadow and greys its label, and ignores clicks`() {
        var clicks = 0
        setPasswordInput(enabled = false, onVisibilityChange = { clicks++ })

        val image = toggle().captureToImage()
        assertTrue(image.countOf(MmoColors.SecondaryButton) > 0)
        assertEquals(0, image.countOf(MmoColors.SecondaryButtonShadow))
        toggle().assertIsNotEnabled().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        toggle().performClick()
        assertEquals(0, clicks)
    }

    @Test
    fun `the toggle keeps one width whether it reads Show or Hide`() {
        var visible by mutableStateOf(false)
        setPasswordInput(passwordVisible = { visible })
        val shown = toggle().getUnclippedBoundsInRoot().width

        visible = true
        composeTestRule.waitForIdle()

        assertEquals(shown, toggle().getUnclippedBoundsInRoot().width)
        composeTestRule.onNodeWithTag("pw_toggle").assertTextEquals("Hide")
    }

    @Test
    @Config(fontScale = 2f)
    fun `at 200 percent font size the field grows rather than clipping its text`() {
        setPasswordInput()
        val height = composeTestRule.onNodeWithTag("pw", useUnmergedTree = true).getUnclippedBoundsInRoot().height
        assertTrue("height was $height", height > Spacing.minTouchTarget)
    }

    private fun setPasswordInput(
        enabled: Boolean = true,
        passwordVisible: () -> Boolean = { false },
        onVisibilityChange: (Boolean) -> Unit = {},
    ) {
        composeTestRule.setContent {
            MmoTheme {
                GdsPasswordInput(
                    state = remember { TextFieldState("secret") },
                    label = "New password",
                    testTag = "pw",
                    passwordVisible = passwordVisible(),
                    onVisibilityChange = onVisibilityChange,
                    labels = GdsPasswordInputLabels("Show password", "Hide password", "shown", "hidden"),
                    enabled = enabled,
                )
            }
        }
    }
}
