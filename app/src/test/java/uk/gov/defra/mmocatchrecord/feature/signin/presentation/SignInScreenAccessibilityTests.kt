package uk.gov.defra.mmocatchrecord.feature.signin.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.gov.defra.mmocatchrecord.common.design.MmoColors
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * WCAG 2.2 AA coverage for the sign-in screens, a legal requirement under the Public Sector Bodies
 * Accessibility Regulations 2018. Robolectric is used because instrumented coverage is not merged in Kover.
 */
@RunWith(RobolectricTestRunner::class)
class SignInScreenAccessibilityTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `the email field advertises the username content type for password managers`() {
        renderSignIn()

        val node =
            composeTestRule
                .onNodeWithTag(SignInScreenTestTags.USERNAME_FIELD, useUnmergedTree = true)
                .fetchSemanticsNode()

        assertEquals(ContentType.Username, node.config[SemanticsProperties.ContentType])
        assertEquals(listOf("Email address"), node.config[SemanticsProperties.ContentDescription])
    }

    @Test
    fun `the password field advertises the password content type for password managers`() {
        renderSignIn()

        val node =
            composeTestRule
                .onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD, useUnmergedTree = true)
                .fetchSemanticsNode()

        assertEquals(ContentType.Password, node.config[SemanticsProperties.ContentType])
        assertEquals(listOf("Password"), node.config[SemanticsProperties.ContentDescription])
    }

    @Test
    fun `both credential fields remain editable so pasting from a password manager is possible`() {
        renderSignIn()

        listOf(SignInScreenTestTags.USERNAME_FIELD, SignInScreenTestTags.PASSWORD_FIELD).forEach { tag ->
            val config = composeTestRule.onNodeWithTag(tag).fetchSemanticsNode().config

            assertTrue(tag, config.contains(SemanticsActions.SetText))
            assertTrue(tag, config.contains(SemanticsActions.InsertTextAtCursor))
            assertFalse(tag, config.contains(SemanticsProperties.Disabled))
        }
    }

    @Test
    fun `the visible field labels are not announced twice`() {
        renderSignIn()

        // The label Text is cleared and voiced by the field's contentDescription instead.
        listOf("Email address", "Password").forEach { label ->
            assertTrue(
                label,
                composeTestRule.onAllNodesWithText(label, useUnmergedTree = true).fetchSemanticsNodes().isEmpty(),
            )
        }
    }

    @Test
    fun `an auth error is a polite live region so it is announced without stealing focus`() {
        renderSignIn(errorMessage = "Your email address or password is incorrect. Check them and try again.")

        val config =
            composeTestRule
                .onNodeWithTag(SignInScreenTestTags.ERROR_MESSAGE, useUnmergedTree = true)
                .fetchSemanticsNode()
                .config

        assertEquals(LiveRegionMode.Polite, config[SemanticsProperties.LiveRegion])
        assertFalse(config.contains(SemanticsProperties.Focused))
        assertFalse(config.contains(SemanticsActions.RequestFocus))
    }

    @Test
    fun `the error is identified in text with a recovery suggestion and never names the wrong field`() {
        val message = "Your email address or password is incorrect. Check them and try again."
        renderSignIn(errorMessage = message)

        val config =
            composeTestRule
                .onNodeWithTag(SignInScreenTestTags.ERROR_MESSAGE, useUnmergedTree = true)
                .fetchSemanticsNode()
                .config
        val announced = config[SemanticsProperties.ContentDescription].single()

        assertTrue(announced.startsWith("Error:"))
        assertTrue(announced.contains("try again"))
        // Account-enumeration control: the copy must never reveal which of the two values was wrong.
        assertFalse(announced.contains("not found"))
        assertFalse(announced.contains("does not exist"))
        assertEquals(listOf(message), config[SemanticsProperties.Text].map { it.text })
    }

    @Test
    fun `the error is announced after the submit action in traversal order`() {
        renderSignIn(errorMessage = "Something went wrong. Try signing in again.")

        val group =
            composeTestRule
                .onNodeWithTag(SignInScreenTestTags.ACTIONS_GROUP, useUnmergedTree = true)
                .fetchSemanticsNode()
        val submitIndex =
            composeTestRule
                .onNodeWithTag(SignInScreenTestTags.SUBMIT_ACTION, useUnmergedTree = true)
                .fetchSemanticsNode()
                .config[SemanticsProperties.TraversalIndex]
        val errorIndex =
            composeTestRule
                .onNodeWithTag(SignInScreenTestTags.ERROR_MESSAGE, useUnmergedTree = true)
                .fetchSemanticsNode()
                .config[SemanticsProperties.TraversalIndex]

        assertTrue(group.config[SemanticsProperties.IsTraversalGroup])
        assertTrue(errorIndex > submitIndex)
    }

    @Test
    fun `the fields are laid out email then password then submit`() {
        renderSignIn()

        val usernameY = topOf(SignInScreenTestTags.USERNAME_FIELD)
        val passwordY = topOf(SignInScreenTestTags.PASSWORD_FIELD)
        val submitY = topOf(SignInScreenTestTags.SUBMIT_ACTION)

        assertTrue(usernameY < passwordY)
        assertTrue(passwordY < submitY)
    }

    @Test
    fun `every interactive target meets the 48dp minimum`() {
        renderSignIn()

        composeTestRule
            .onNodeWithTag(SignInScreenTestTags.LANGUAGE_TOGGLE)
            .performScrollTo()
            .assertHeightIsAtLeast(MIN_TARGET)
            .assertWidthIsAtLeast(MIN_TARGET)
        composeTestRule
            .onNodeWithTag(SignInScreenTestTags.SUBMIT_ACTION)
            .performScrollTo()
            .assertHeightIsAtLeast(MIN_TARGET)
        composeTestRule
            .onNodeWithTag(SignInScreenTestTags.FORGOTTEN_PASSWORD)
            .performScrollTo()
            .assertHeightIsAtLeast(MIN_TARGET)
    }

    @Test
    fun `the language toggle and the password link are exposed as buttons`() {
        renderSignIn()

        assertEquals(
            Role.Button,
            composeTestRule
                .onNodeWithTag(SignInScreenTestTags.LANGUAGE_TOGGLE)
                .fetchSemanticsNode()
                .config[SemanticsProperties.Role],
        )
        assertEquals(
            listOf("Switch to Welsh"),
            composeTestRule
                .onNodeWithTag(SignInScreenTestTags.LANGUAGE_TOGGLE)
                .fetchSemanticsNode()
                .config[SemanticsProperties.ContentDescription],
        )
        assertEquals(
            Role.Button,
            composeTestRule
                .onNodeWithTag(SignInScreenTestTags.FORGOTTEN_PASSWORD)
                .fetchSemanticsNode()
                .config[SemanticsProperties.Role],
        )
    }

    @Test
    fun `the screen still renders every control at the maximum supported font scale`() {
        composeTestRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = MAX_FONT_SCALE),
            ) {
                MmoTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        SignInContent(
                            state = SignInViewState(status = UiStatus.Error(GENERIC_ERROR, isRetryable = false)),
                            currentLanguage = "en",
                            onLanguageToggle = {},
                            onUsernameChanged = {},
                            onPasswordChanged = {},
                            onSubmit = {},
                        )
                    }
                }
            }
        }

        composeTestRule.onNodeWithTag(SignInScreenTestTags.USERNAME_FIELD).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag(SignInScreenTestTags.PASSWORD_FIELD).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag(SignInScreenTestTags.ERROR_MESSAGE).performScrollTo().assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(SignInScreenTestTags.SUBMIT_ACTION)
            .performScrollTo()
            .assertIsDisplayed()
            .assertHeightIsAtLeast(MIN_TARGET)
    }

    @Test
    fun `sign-in text colours meet the 4 point 5 to 1 minimum contrast ratio on white`() {
        listOf(MmoColors.Text, MmoColors.Grey1, MmoColors.Link, MmoColors.ErrorRed).forEach { foreground ->
            assertTrue(
                foreground.toString(),
                contrastRatio(foreground, MmoColors.White) >= MIN_CONTRAST,
            )
        }
    }

    private fun renderSignIn(errorMessage: String? = null) {
        composeTestRule.setContent {
            MmoTheme {
                SignInContent(
                    state =
                        SignInViewState(
                            status = errorMessage?.let { UiStatus.Error(it, isRetryable = false) } ?: UiStatus.Idle,
                        ),
                    currentLanguage = "en",
                    onLanguageToggle = {},
                    onUsernameChanged = {},
                    onPasswordChanged = {},
                    onSubmit = {},
                )
            }
        }
    }

    private fun topOf(tag: String): Float =
        composeTestRule
            .onNodeWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNode()
            .positionInRoot.y

    private fun contrastRatio(
        foreground: Color,
        background: Color,
    ): Double {
        val first = relativeLuminance(foreground)
        val second = relativeLuminance(background)
        return (max(first, second) + 0.05) / (min(first, second) + 0.05)
    }

    private fun relativeLuminance(color: Color): Double {
        fun channel(value: Float): Double {
            val v = value.toDouble()
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    private companion object {
        val MIN_TARGET = 48.dp
        const val MAX_FONT_SCALE = 2f
        const val MIN_CONTRAST = 4.5
        const val GENERIC_ERROR = "Something went wrong. Try signing in again."
    }
}
