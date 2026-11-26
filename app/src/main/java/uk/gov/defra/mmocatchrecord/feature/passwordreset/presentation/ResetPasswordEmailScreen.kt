@file:Suppress("detekt.FunctionNaming")

package uk.gov.defra.mmocatchrecord.feature.passwordreset.presentation

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.common.design.GdsTextInput
import uk.gov.defra.mmocatchrecord.common.design.MmoColors
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.common.design.PrimaryActionButton
import uk.gov.defra.mmocatchrecord.common.design.Spacing

/** Step 1: ask for the email address and send a reset code (AC1 to AC3). */
@Composable
@Suppress("LongParameterList")
fun ResetPasswordEmailContent(
    state: ResetPasswordViewState,
    emailState: TextFieldState,
    chrome: ResetChrome,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    actions: ResetScreenActions = ResetScreenActions(),
) {
    val focus = remember { ResetFieldFocus() }
    ResetPasswordScaffold(
        screenTestTag = ResetPasswordTestTags.EMAIL_SCREEN,
        title = stringResource(R.string.reset_password_email_title),
        state = state,
        chrome = chrome,
        actions = actions,
        modifier = modifier,
        focus = focus,
        firstField = ResetField.Email,
        showLogo = true,
    ) {
        Text(
            text = stringResource(R.string.reset_password_email_guidance),
            style = MaterialTheme.typography.bodyLarge.copy(color = MmoColors.Text),
            modifier = Modifier.testTag(ResetPasswordTestTags.GUIDANCE),
        )
        GdsTextInput(
            state = emailState,
            label = stringResource(R.string.email_address),
            hint = stringResource(R.string.reset_password_email_hint),
            testTag = ResetPasswordTestTags.EMAIL_FIELD,
            errorMessage = state.errorFor(ResetField.Email),
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done,
                    autoCorrectEnabled = false,
                ),
            onKeyboardAction = KeyboardActionHandler { onSubmit() },
            contentType = ContentType.Username + ContentType.EmailAddress,
            focusRequester = focus.of(ResetField.Email),
            enabled = !state.isLoading,
        )
        PrimaryActionButton(
            text = stringResource(R.string.reset_password_send_code),
            onClick = onSubmit,
            enabled = state.canSubmit,
            modifier = Modifier.padding(top = Spacing.l).testTag(ResetPasswordTestTags.SUBMIT_ACTION),
        )
    }
}

/** The resolved message for [field], if it has one; resolved here so the language toggle re-translates it. */
@Composable
internal fun ResetPasswordViewState.errorFor(field: ResetField): String? =
    fieldErrors.firstOrNull { it.field == field }?.let { stringResource(it.messageRes) }

/** Submit is blocked while a request is in flight or the device is offline (AC18). */
internal val ResetPasswordViewState.canSubmit: Boolean
    get() = !isLoading && !isOffline

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 390, heightDp = 844)
@Composable
fun ResetPasswordEmailPreview() {
    MmoTheme {
        ResetPasswordEmailContent(
            state = ResetPasswordViewState(),
            emailState = remember { TextFieldState() },
            chrome = ResetChrome("en") {},
            onSubmit = {},
        )
    }
}
