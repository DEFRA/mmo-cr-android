package uk.gov.defra.mmocatchrecord.common.design

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private val MIN_TARGET = 48.dp
private const val ERROR_MESSAGE = "Enter an email address"

/** Robolectric accessibility checks for the GOV.UK form components (labels, errors, targets, live regions). */
@RunWith(RobolectricTestRunner::class)
class GdsFormComponentsTests {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun labelsFor(name: String) =
        GdsPasswordInputLabels(
            showContentDescription = "Show $name",
            hideContentDescription = "Hide $name",
            visibleAnnouncement = "$name shown",
            hiddenAnnouncement = "$name hidden",
        )

    private fun node(tag: String) = composeTestRule.onNodeWithTag(tag, useUnmergedTree = true)

    @Test
    fun textInput_isNamedByLabelAndHint_andShowsThem() {
        composeTestRule.setContent {
            MmoTheme {
                GdsTextInput(
                    TextFieldState(),
                    label = "Email address",
                    hint = "Like name@example.com",
                    testTag = "email",
                )
            }
        }

        node("email")
            .assert(
                SemanticsMatcher("named by label then hint") {
                    it.config
                        .getOrNull(SemanticsProperties.ContentDescription)
                        ?.firstOrNull() == "Email address. Like name@example.com"
                },
            ).assertHeightIsAtLeast(MIN_TARGET)
        node("email_label").assertIsDisplayed()
        node("email_hint").assertIsDisplayed()
        composeTestRule.onNodeWithTag("email_error", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun textInput_error_isInSemanticsWithPrefix_andShownAsTextAndIcon() {
        composeTestRule.setContent {
            MmoTheme {
                GdsTextInput(
                    TextFieldState(),
                    label = "Email",
                    errorMessage = ERROR_MESSAGE,
                    testTag = "email",
                )
            }
        }

        node("email").assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "Error: $ERROR_MESSAGE"))
        node("email_error").assertIsDisplayed()
    }

    @Test
    fun textInput_acceptsAKeyboardActionAndFocusRequester() {
        var done = 0
        composeTestRule.setContent {
            MmoTheme {
                GdsTextInput(
                    TextFieldState("a"),
                    label = "Code",
                    testTag = "code",
                    onKeyboardAction = { done++ },
                    focusRequester = remember { FocusRequester() },
                    enabled = true,
                )
            }
        }

        node("code").assertIsDisplayed()
        assertEquals(0, done)
    }

    @Test
    fun passwordInput_showHideToggleHasOwnSpokenNameContainingVisibleText() {
        composeTestRule.setContent {
            MmoTheme {
                var visible by remember { mutableStateOf(false) }
                GdsPasswordInput(
                    state = remember { TextFieldState("secret") },
                    label = "New password",
                    testTag = "pw",
                    passwordVisible = visible,
                    onVisibilityChange = { visible = it },
                    labels = labelsFor("new password"),
                )
            }
        }

        node("pw_toggle")
            .assertHeightIsAtLeast(MIN_TARGET)
            .assertWidthIsAtLeast(MIN_TARGET)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Show new password")))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))

        node("pw_toggle").performClick()

        node("pw_toggle")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Hide new password")))
    }

    @Test
    fun passwordInput_announcesStateOnlyAfterToggle_inAPoliteLiveRegion() {
        composeTestRule.setContent {
            MmoTheme {
                var visible by remember { mutableStateOf(false) }
                GdsPasswordInput(
                    state = remember { TextFieldState("x") },
                    label = "Password",
                    testTag = "pw",
                    passwordVisible = visible,
                    onVisibilityChange = { visible = it },
                    labels = labelsFor("password"),
                )
            }
        }

        composeTestRule.onNodeWithTag("pw_status", useUnmergedTree = true).assertDoesNotExist()

        node("pw_toggle").performClick()

        node("pw_status").assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        node(
            "pw_status",
        ).assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("password shown")))
        node("pw_toggle").performClick()
        node(
            "pw_status",
        ).assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("password hidden")))
    }

    @Test
    fun twoPasswordInputs_toggleIndependently() {
        composeTestRule.setContent {
            MmoTheme {
                var first by remember { mutableStateOf(false) }
                var second by remember { mutableStateOf(false) }
                Column {
                    GdsPasswordInput(
                        state = remember { TextFieldState("a") },
                        label = "New password",
                        testTag = "first",
                        passwordVisible = first,
                        onVisibilityChange = { first = it },
                        labels = labelsFor("new password"),
                    )
                    GdsPasswordInput(
                        state = remember { TextFieldState("a") },
                        label = "Confirm password",
                        testTag = "second",
                        passwordVisible = second,
                        onVisibilityChange = { second = it },
                        labels = labelsFor("confirm password"),
                    )
                }
            }
        }

        node("first_toggle").performClick()

        node(
            "first_toggle",
        ).assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Hide new password")))
        node(
            "second_toggle",
        ).assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Show confirm password")))
    }

    @Test
    fun passwordInput_error_isInSemanticsWithPrefix() {
        composeTestRule.setContent {
            MmoTheme {
                GdsPasswordInput(
                    state = remember { TextFieldState() },
                    label = "Password",
                    testTag = "pw",
                    errorMessage = "Passwords do not match",
                    passwordVisible = false,
                    onVisibilityChange = {},
                    labels = labelsFor("password"),
                    hint = "At least 8 characters",
                )
            }
        }

        node("pw").assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "Error: Passwords do not match"))
        node("pw_error").assertIsDisplayed()
    }

    @Test
    fun errorSummary_isAnAssertiveFocusedLiveRegionWithHeadingAndLinks() {
        val requester = FocusRequester()
        var clicked = -1
        composeTestRule.setContent {
            MmoTheme {
                GdsErrorSummary(
                    items =
                        listOf(
                            GdsErrorSummaryItem("First problem") { clicked = 0 },
                            GdsErrorSummaryItem("Second problem") { clicked = 1 },
                        ),
                    focusRequester = requester,
                    testTag = "summary",
                )
            }
        }

        node("summary")
            .assertIsFocused()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Assertive))
        node("summary_item_1")
            .assertHeightIsAtLeast(MIN_TARGET)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        assertEquals(1, clicked)
        composeTestRule.onNodeWithTag("summary_item_0", useUnmergedTree = true).performClick()
        assertEquals(0, clicked)
    }

    @Test
    fun errorSummary_refocusesWhenTheTriggerChanges() {
        val requester = FocusRequester()
        val otherField = FocusRequester()
        var trigger by mutableStateOf(0)
        composeTestRule.setContent {
            MmoTheme {
                Column {
                    GdsErrorSummary(
                        items = listOf(GdsErrorSummaryItem("Problem") {}),
                        focusRequester = requester,
                        testTag = "summary",
                        focusTrigger = trigger,
                    )
                    GdsTextInput(TextFieldState(), label = "Other", testTag = "other", focusRequester = otherField)
                }
            }
        }
        node("summary").assertIsFocused()
        composeTestRule.runOnIdle { otherField.requestFocus() }
        node("other").assertIsFocused()

        trigger = 1
        composeTestRule.waitForIdle()

        node("summary").assertIsFocused()
    }

    @Test
    fun offlineNotice_isANamedPoliteLiveRegion_withARecoveryAction() {
        var retried = 0
        composeTestRule.setContent {
            MmoTheme { GdsOfflineNotice(actionLabel = "Try again", onAction = { retried++ }) }
        }

        node("gds_offline_notice")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle))
            .assertIsDisplayed()
        node("gds_offline_notice_action").assertHeightIsAtLeast(MIN_TARGET).performClick()
        assertEquals(1, retried)
        node("gds_offline_notice_status")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    @Test
    fun secureScreen_setsFlagWhileComposed_andClearsItOnLeave() {
        var show by mutableStateOf(true)
        composeTestRule.setContent { if (show) SecureScreen() }
        val window = composeTestRule.activity.window

        composeTestRule.waitForIdle()
        assertTrue(window.isSecure())

        show = false
        composeTestRule.waitForIdle()
        assertFalse(window.isSecure())
    }

    @Test
    fun secureScreen_doesNotClearAFlagItDidNotSet() {
        val window = composeTestRule.activity.window
        assertTrue(enableSecureFlag(window))
        assertFalse(enableSecureFlag(window))
        disableSecureFlag(window)
        assertFalse(window.isSecure())
    }

    private fun android.view.Window.isSecure() =
        attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0
}
