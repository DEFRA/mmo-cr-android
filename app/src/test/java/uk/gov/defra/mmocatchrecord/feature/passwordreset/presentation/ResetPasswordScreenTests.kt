package uk.gov.defra.mmocatchrecord.feature.passwordreset.presentation

import android.content.ActivityNotFoundException
import android.content.Intent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.common.design.AppLanguageProvider
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme

/**
 * Robolectric coverage of the four reset screens (AC1 to AC19 UI behaviour and WCAG 2.2 AA). The stateless
 * `…Content` composables are tested because `hiltViewModel()` needs a Hilt test host.
 */
@RunWith(RobolectricTestRunner::class)
@Suppress("LargeClass", "TooManyFunctions")
class ResetPasswordScreenTests {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val chrome = ResetChrome("en") {}

    private fun render(
        language: String = "en",
        fontScale: Float = 1f,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MmoTheme { AppLanguageProvider(language = language) { content() } }
            }
        }
    }

    private fun pressBack() {
        composeTestRule.runOnUiThread { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()
    }

    private fun typed(value: String = "") = TextFieldState().also { it.setTextAndPlaceCursorAtEnd(value) }

    private fun inSummary(text: String) =
        hasText(text) and hasAnyAncestor(hasTestTag(ResetPasswordTestTags.ERROR_SUMMARY))

    // ---- Email screen ----

    @Test
    fun `the email screen shows its heading, field, guidance and send button`() {
        render { ResetPasswordEmailContent(ResetPasswordViewState(), typed(), chrome, onSubmit = {}) }

        composeTestRule.onNodeWithTag(ResetPasswordTestTags.EMAIL_SCREEN).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ResetPasswordTestTags.HEADING).assertIsDisplayed()
        composeTestRule.onNodeWithText("Reset your password").assertIsDisplayed()
        composeTestRule.onNodeWithTag(ResetPasswordTestTags.EMAIL_FIELD).assertIsDisplayed()
        composeTestRule.onNodeWithText("Send reset code").assertIsDisplayed()
    }

    @Test
    fun `the heading is a semantic heading and the screen has a pane title`() {
        render { ResetPasswordEmailContent(ResetPasswordViewState(), typed(), chrome, onSubmit = {}) }

        val heading = composeTestRule.onNodeWithTag(ResetPasswordTestTags.HEADING).fetchSemanticsNode()
        assertNotNull(heading.config.getOrNull(SemanticsProperties.Heading))
        val pane =
            composeTestRule
                .onNode(
                    SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle),
                ).fetchSemanticsNode()
        assertEquals("Reset your password", pane.config[SemanticsProperties.PaneTitle])
    }

    @Test
    fun `the email field offers the username and email autofill types`() {
        render { ResetPasswordEmailContent(ResetPasswordViewState(), typed(), chrome, onSubmit = {}) }

        val node =
            composeTestRule
                .onNodeWithTag(
                    ResetPasswordTestTags.EMAIL_FIELD,
                    useUnmergedTree = true,
                ).fetchSemanticsNode()

        assertNotNull(node.config.getOrNull(SemanticsProperties.ContentType))
    }

    @Test
    fun `tapping send and the keyboard Done action both submit`() {
        var submits = 0
        render {
            ResetPasswordEmailContent(
                ResetPasswordViewState(),
                typed("a@b.co"),
                chrome,
                onSubmit = { submits++ },
            )
        }

        composeTestRule.onNodeWithTag(ResetPasswordTestTags.SUBMIT_ACTION).performClick()
        composeTestRule.onNodeWithTag(ResetPasswordTestTags.EMAIL_FIELD).performImeAction()

        assertEquals(2, submits)
    }

    @Test
    fun `field errors show in the summary and inline and the summary link moves focus to the field`() {
        val message = string(R.string.reset_password_email_error_blank)
        val state =
            ResetPasswordViewState(
                fieldErrors = listOf(ResetFieldError(ResetField.Email, R.string.reset_password_email_error_blank)),
                errorSequence = 1,
            )
        render { ResetPasswordEmailContent(state, typed(), chrome, onSubmit = {}) }

        composeTestRule.onNodeWithTag(ResetPasswordTestTags.ERROR_SUMMARY).assertIsDisplayed()
        composeTestRule.onNode(inSummary(message)).assertIsDisplayed()
        assertEquals(2, count(message))

        composeTestRule.onNode(inSummary(message)).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(ResetPasswordTestTags.EMAIL_FIELD).assertIsFocused()
    }

    @Test
    fun `a page error shows in the summary`() {
        val state = ResetPasswordViewState(pageErrorRes = R.string.reset_password_error_network, errorSequence = 1)
        render { ResetPasswordEmailContent(state, typed(), chrome, onSubmit = {}) }

        composeTestRule.onNode(inSummary(string(R.string.reset_password_error_network))).assertIsDisplayed()
    }

    @Test
    fun `loading disables the button and announces progress politely`() {
        render { ResetPasswordEmailContent(ResetPasswordViewState(isLoading = true), typed("a@b.co"), chrome, {}) }

        composeTestRule.onNodeWithTag(ResetPasswordTestTags.SUBMIT_ACTION).assertIsNotEnabled()
        val loading = composeTestRule.onNodeWithTag(ResetPasswordTestTags.LOADING).fetchSemanticsNode()
        assertEquals(LiveRegionMode.Polite, loading.config[SemanticsProperties.LiveRegion])
    }

    @Test
    fun `offline shows the notice, disables submit and the action re-checks the connection`() {
        var rechecks = 0
        var offline by mutableStateOf(true)
        val email = typed("a@b.co")
        render {
            ResetPasswordEmailContent(
                state = ResetPasswordViewState(isOffline = offline),
                emailState = email,
                chrome = chrome,
                onSubmit = {},
                actions = ResetScreenActions(onRecheckConnection = { rechecks++ }),
            )
        }

        composeTestRule.onNodeWithTag(ResetPasswordTestTags.OFFLINE_NOTICE).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ResetPasswordTestTags.SUBMIT_ACTION).assertIsNotEnabled()
        composeTestRule.onNodeWithTag("${ResetPasswordTestTags.OFFLINE_NOTICE}_action").performClick()
        assertEquals(1, rechecks)

        offline = false
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(ResetPasswordTestTags.OFFLINE_NOTICE).assertDoesNotExist()
        composeTestRule.onNodeWithTag(ResetPasswordTestTags.SUBMIT_ACTION).assertIsEnabled()
    }

    @Test
    fun `system Back and the top bar back both call onBack`() {
        var backs = 0
        render {
            ResetPasswordEmailContent(
                ResetPasswordViewState(),
                typed(),
                chrome,
                onSubmit = {},
                actions = ResetScreenActions(onBack = { backs++ }),
            )
        }

        pressBack()
        composeTestRule.onNodeWithText("Back").performClick()

        assertEquals(2, backs)
    }

    @Test
    fun `the browser hand-off notice is announced politely and acknowledged once shown`() {
        var shown = 0
        render {
            ResetPasswordEmailContent(
                ResetPasswordViewState(isRedirectingToBrowser = true),
                typed(),
                chrome,
                onSubmit = {},
                actions = ResetScreenActions(onBrowserNoticeShown = { shown++ }),
            )
        }

        val node = composeTestRule.onNodeWithTag(ResetPasswordTestTags.BROWSER_HANDOFF_MESSAGE).fetchSemanticsNode()
        assertEquals(LiveRegionMode.Polite, node.config[SemanticsProperties.LiveRegion])
        assertEquals(1, shown)
    }

    @Test
    fun `controls meet the 48dp touch target and the screen works at 200 percent font scale`() {
        render(fontScale = 2f) { ResetPasswordEmailContent(ResetPasswordViewState(), typed(), chrome, {}) }

        composeTestRule
            .onNodeWithTag(
                ResetPasswordTestTags.SUBMIT_ACTION,
            ).performScrollTo()
            .assertHeightIsAtLeast(48.dp)
        composeTestRule.onNodeWithTag(ResetPasswordTestTags.EMAIL_FIELD).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `the email screen is shown in Welsh when the in-app language is Welsh`() {
        render(language = "cy") {
            ResetPasswordEmailContent(ResetPasswordViewState(), typed(), ResetChrome("cy") {}, onSubmit = {})
        }

        composeTestRule.onNodeWithText("Ailosod eich cyfrinair").assertIsDisplayed()
        composeTestRule.onNodeWithText("Anfon cod ailosod").assertIsDisplayed()
    }

    // ---- Code screen ----

    @Test
    fun `the code screen names no address, shows the lifetime hint and the three actions`() {
        render {
            ResetPasswordCodeContent(
                ResetPasswordViewState(step = ResetStep.CodeEntry, codeLifetimeMinutes = 5),
                typed(),
                chrome,
                onSubmit = {},
                onSendNewCode = {},
            )
        }

        composeTestRule.onNodeWithTag(ResetPasswordTestTags.CODE_SCREEN).assertIsDisplayed()
        composeTestRule.onNodeWithText("Enter your reset code").assertIsDisplayed()
        assertTrue(fieldName(ResetPasswordTestTags.CODE_FIELD).contains("The code is valid for 5 minutes."))
        composeTestRule.onNodeWithText("Continue").assertIsDisplayed()
        composeTestRule.onNodeWithTag(ResetPasswordTestTags.SEND_NEW_CODE_ACTION).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag(ResetPasswordTestTags.OPEN_EMAIL_APP_ACTION).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag(ResetPasswordTestTags.GUIDANCE).assert(!hasText("@", substring = true))
    }

    @Test
    fun `a one minute lifetime uses the singular and an unknown lifetime shows no hint`() {
        render {
            ResetPasswordCodeContent(
                ResetPasswordViewState(step = ResetStep.CodeEntry, codeLifetimeMinutes = 1),
                typed(),
                chrome,
                {},
                {},
            )
        }
        assertTrue(fieldName(ResetPasswordTestTags.CODE_FIELD).contains("The code is valid for 1 minute."))
    }

    @Test
    fun `no hint is shown when the lifetime is unknown`() {
        render { ResetPasswordCodeContent(ResetPasswordViewState(codeLifetimeMinutes = 0), typed(), chrome, {}, {}) }

        assertEquals(0, count("The code is valid for", substring = true))
    }

    @Test
    fun `Send a new code, system Back and the top bar back all send a new code`() {
        var sends = 0
        render { ResetPasswordCodeContent(ResetPasswordViewState(), typed(), chrome, {}, onSendNewCode = { sends++ }) }

        composeTestRule.onNodeWithTag(ResetPasswordTestTags.SEND_NEW_CODE_ACTION).performScrollTo().performClick()
        pressBack()
        composeTestRule.onNodeWithText("Back").performClick()

        assertEquals(3, sends)
    }

    @Test
    fun `Continue and the keyboard Done action submit the code`() {
        var submits = 0
        render {
            ResetPasswordCodeContent(ResetPasswordViewState(), typed("123456"), chrome, onSubmit = { submits++ }, {})
        }

        composeTestRule.onNodeWithTag(ResetPasswordTestTags.SUBMIT_ACTION).performClick()
        composeTestRule.onNodeWithTag(ResetPasswordTestTags.CODE_FIELD).performImeAction()

        assertEquals(2, submits)
    }

    @Test
    fun `the code field shows an inline and summary error`() {
        val state =
            ResetPasswordViewState(
                fieldErrors = listOf(ResetFieldError(ResetField.Code, R.string.reset_password_code_error_expired)),
                errorSequence = 1,
            )
        render { ResetPasswordCodeContent(state, typed(), chrome, {}, {}) }

        assertEquals(2, count(string(R.string.reset_password_code_error_expired)))
    }

    @Test
    fun `the email app button calls back and the unavailable message is shown when no app exists`() {
        var opened = 0
        var unavailable by mutableStateOf(false)
        render {
            ResetPasswordCodeContent(
                ResetPasswordViewState(),
                typed(),
                chrome,
                {},
                {},
                emailAppUnavailable = unavailable,
                onOpenEmailApp = { opened++ },
            )
        }

        composeTestRule.onNodeWithTag(ResetPasswordTestTags.OPEN_EMAIL_APP_ACTION).performScrollTo().performClick()
        assertEquals(1, opened)
        composeTestRule.onNodeWithTag(ResetPasswordTestTags.EMAIL_APP_UNAVAILABLE).assertDoesNotExist()

        unavailable = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(ResetPasswordTestTags.EMAIL_APP_UNAVAILABLE).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `launchEmailApp starts the email category intent and reports a missing email app`() {
        var started: Intent? = null

        assertTrue(launchEmailApp { started = it })
        assertEquals(Intent.ACTION_MAIN, started?.selector?.action ?: started?.action)
        assertTrue(started?.selector?.categories?.contains(Intent.CATEGORY_APP_EMAIL) == true)
        assertFalse(launchEmailApp { throw ActivityNotFoundException() })
    }

    // ---- New password screen ----

    private fun newPasswordFields(
        newPassword: String = "",
        confirm: String = "",
        onNewVisible: (Boolean) -> Unit = {},
        onConfirmVisible: (Boolean) -> Unit = {},
    ) = NewPasswordFields(typed(newPassword), typed(confirm), onNewVisible, onConfirmVisible)

    @Test
    fun `the new password screen shows both fields, the policy hint and the save button`() {
        render {
            CreateNewPasswordContent(
                ResetPasswordViewState(step = ResetStep.NewPassword),
                newPasswordFields(),
                chrome,
                onSubmit = {},
            )
        }

        composeTestRule.onNodeWithTag(ResetPasswordTestTags.NEW_PASSWORD_SCREEN).assertIsDisplayed()
        composeTestRule.onNodeWithText("Create a new password").assertIsDisplayed()
        assertTrue(
            fieldName(
                ResetPasswordTestTags.NEW_PASSWORD_FIELD,
            ).contains(string(R.string.reset_password_new_hint, 8, 256, 3)),
        )
        composeTestRule.onNodeWithTag(ResetPasswordTestTags.NEW_PASSWORD_FIELD).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ResetPasswordTestTags.CONFIRM_PASSWORD_FIELD).assertIsDisplayed()
        composeTestRule.onNodeWithText("Save new password").assertIsDisplayed()
    }

    @Test
    fun `both password fields advertise the new-password autofill type`() {
        render { CreateNewPasswordContent(ResetPasswordViewState(), newPasswordFields(), chrome, onSubmit = {}) }

        listOf(ResetPasswordTestTags.NEW_PASSWORD_FIELD, ResetPasswordTestTags.CONFIRM_PASSWORD_FIELD).forEach { tag ->
            val node = composeTestRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            assertEquals(ContentType.NewPassword, node.config[SemanticsProperties.ContentType])
        }
    }

    @Test
    fun `each password field has its own show and hide toggle`() {
        val toggles = mutableListOf<String>()
        render {
            CreateNewPasswordContent(
                ResetPasswordViewState(),
                newPasswordFields(
                    onNewVisible = { toggles += "new:$it" },
                    onConfirmVisible = { toggles += "confirm:$it" },
                ),
                chrome,
                onSubmit = {},
            )
        }

        composeTestRule.onNodeWithContentDescription("Show new password").performClick()
        composeTestRule.onNodeWithContentDescription("Show confirmation of new password").performClick()

        assertEquals(listOf("new:true", "confirm:true"), toggles)
    }

    @Test
    fun `the visible state shows the hide labels`() {
        render {
            CreateNewPasswordContent(
                ResetPasswordViewState(newPasswordVisible = true, confirmPasswordVisible = true),
                newPasswordFields(),
                chrome,
                onSubmit = {},
            )
        }

        composeTestRule.onNodeWithContentDescription("Hide new password").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Hide confirmation of new password").assertIsDisplayed()
    }

    @Test
    fun `save and the keyboard Done action on the confirm field submit`() {
        var submits = 0
        render {
            CreateNewPasswordContent(
                ResetPasswordViewState(),
                newPasswordFields("Abcdef1!", "Abcdef1!"),
                chrome,
                onSubmit = { submits++ },
            )
        }

        composeTestRule.onNodeWithTag(ResetPasswordTestTags.SUBMIT_ACTION).performScrollTo().performClick()
        composeTestRule.onNodeWithTag(ResetPasswordTestTags.CONFIRM_PASSWORD_FIELD).performImeAction()

        assertEquals(2, submits)
    }

    @Test
    fun `password errors show inline against the right field and in the summary`() {
        val state =
            ResetPasswordViewState(
                fieldErrors =
                    listOf(
                        ResetFieldError(ResetField.NewPassword, R.string.reset_password_new_error_requirements),
                        ResetFieldError(ResetField.ConfirmPassword, R.string.reset_password_confirm_error_mismatch),
                    ),
                errorSequence = 1,
            )
        render { CreateNewPasswordContent(state, newPasswordFields(), chrome, onSubmit = {}) }

        listOf(R.string.reset_password_new_error_requirements, R.string.reset_password_confirm_error_mismatch).forEach {
            assertEquals(2, count(string(it)))
        }
    }

    @Test
    fun `offline disables saving`() {
        render {
            CreateNewPasswordContent(ResetPasswordViewState(isOffline = true), newPasswordFields(), chrome, {})
        }

        composeTestRule.onNodeWithTag(ResetPasswordTestTags.SUBMIT_ACTION).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `the screen is excluded from screenshots while shown and the flag is cleared on leaving`() {
        var show by mutableStateOf(true)
        render {
            if (show) {
                CreateNewPasswordContent(ResetPasswordViewState(), newPasswordFields(), chrome, onSubmit = {})
            }
        }
        val flags = { composeTestRule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE }

        assertTrue(flags() != 0)

        show = false
        composeTestRule.waitForIdle()
        assertEquals(0, flags())
    }

    @Test
    fun `Back asks before cancelling and staying keeps the user on the screen`() {
        var backs = 0
        render {
            CreateNewPasswordContent(
                ResetPasswordViewState(),
                newPasswordFields(),
                chrome,
                onSubmit = {},
                actions = ResetScreenActions(onBack = { backs++ }),
            )
        }

        pressBack()
        composeTestRule.onNodeWithTag(ResetPasswordTestTags.CANCEL_DIALOG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel resetting your password?").assertIsDisplayed()
        composeTestRule.onNodeWithTag(ResetPasswordTestTags.CANCEL_DIALOG_DISMISS).performClick()

        composeTestRule.onNodeWithTag(ResetPasswordTestTags.CANCEL_DIALOG).assertDoesNotExist()
        assertEquals(0, backs)
        composeTestRule.onNodeWithTag(ResetPasswordTestTags.NEW_PASSWORD_SCREEN).assertIsDisplayed()
    }

    @Test
    fun `confirming the cancel dialog leaves, from the top bar back as well`() {
        var backs = 0
        render {
            CreateNewPasswordContent(
                ResetPasswordViewState(),
                newPasswordFields(),
                chrome,
                onSubmit = {},
                actions = ResetScreenActions(onBack = { backs++ }),
            )
        }

        composeTestRule.onNodeWithText("Back").performClick()
        composeTestRule.onNodeWithTag(ResetPasswordTestTags.CANCEL_DIALOG_CONFIRM).performClick()

        assertEquals(1, backs)
        composeTestRule.onNodeWithTag(ResetPasswordTestTags.CANCEL_DIALOG).assertDoesNotExist()
    }

    @Test
    fun `the cancel dialog is in Welsh when the language is Welsh`() {
        render(language = "cy") {
            CreateNewPasswordContent(ResetPasswordViewState(), newPasswordFields(), ResetChrome("cy") {}, {})
        }

        pressBack()

        composeTestRule.onNodeWithText("Canslo ailosod eich cyfrinair?").assertIsDisplayed()
    }

    // ---- Success screen ----

    @Test
    fun `the success screen confirms the reset and Sign in returns to sign in`() {
        var signIns = 0
        render {
            PasswordResetSuccessContent(
                ResetPasswordViewState(step = ResetStep.Success),
                chrome,
                onSignIn = { signIns++ },
            )
        }

        composeTestRule.onNodeWithTag(ResetPasswordTestTags.SUCCESS_SCREEN).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ResetPasswordTestTags.SUCCESS_BANNER).assertIsDisplayed()
        composeTestRule.onNodeWithText("Your password has been reset").assertIsDisplayed()
        composeTestRule.onNodeWithText("You can now sign in with your new password.").assertIsDisplayed()

        composeTestRule.onNodeWithTag(ResetPasswordTestTags.SIGN_IN_ACTION).performClick()
        assertEquals(1, signIns)
    }

    @Test
    fun `Back on the success screen also returns to sign in and offers no way back to the old steps`() {
        var signIns = 0
        render {
            PasswordResetSuccessContent(ResetPasswordViewState(step = ResetStep.Success), chrome, { signIns++ })
        }

        pressBack()

        assertEquals(1, signIns)
        composeTestRule.onNodeWithTag(ResetPasswordTestTags.SUBMIT_ACTION).assertDoesNotExist()
    }

    private fun string(
        id: Int,
        vararg args: Any,
    ) = composeTestRule.activity.getString(id, *args)

    /** The accessible name TalkBack reads for a field: its label followed by the hint. */
    private fun fieldName(tag: String) =
        composeTestRule
            .onNodeWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription]
            .joinToString(" ")

    private fun count(
        text: String,
        substring: Boolean = false,
    ) = composeTestRule
        .onAllNodesWithText(
            text,
            substring = substring,
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
        .size
}
