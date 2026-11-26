@file:Suppress("detekt.FunctionNaming")

package uk.gov.defra.mmocatchrecord.feature.passwordreset.presentation

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.common.design.GdsTextInput
import uk.gov.defra.mmocatchrecord.common.design.MmoColors
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.common.design.PrimaryActionButton
import uk.gov.defra.mmocatchrecord.common.design.SecondaryActionButton

/**
 * Step 2: enter the reset code (AC4 to AC9); copy never names the email, code length or channel (AC3).
 */
@Composable
@Suppress("LongParameterList")
fun ResetPasswordCodeContent(
    state: ResetPasswordViewState,
    codeState: TextFieldState,
    chrome: ResetChrome,
    onSubmit: () -> Unit,
    onSendNewCode: () -> Unit,
    modifier: Modifier = Modifier,
    actions: ResetScreenActions = ResetScreenActions(),
) {
    val focus = remember { ResetFieldFocus() }
    ResetPasswordScaffold(
        screenTestTag = ResetPasswordTestTags.CODE_SCREEN,
        title = stringResource(R.string.reset_password_code_title),
        state = state,
        chrome = chrome,
        actions = actions,
        modifier = modifier,
        focus = focus,
        firstField = ResetField.Code,
        // Back and the top-bar back both leave this step the same way as "Send a new code" (Q8).
        onBack = onSendNewCode,
        showLogo = true,
    ) {
        Text(
            text = stringResource(R.string.reset_password_code_sent_message),
            style = MaterialTheme.typography.bodyLarge.copy(color = MmoColors.Text),
            modifier = Modifier.testTag(ResetPasswordTestTags.GUIDANCE),
        )
        state.codeLifetimeMinutes.takeIf { it > 0 }?.let {
            Text(
                text = pluralStringResource(R.plurals.reset_password_code_hint, it, it),
                style = MaterialTheme.typography.bodyLarge.copy(color = MmoColors.Text),
                modifier = Modifier.testTag(ResetPasswordTestTags.CODE_EXPIRY),
            )
        }
        CodeField(state, codeState, focus, onSubmit)
        PrimaryActionButton(
            text = stringResource(R.string.reset_password_continue),
            onClick = onSubmit,
            enabled = state.canSubmit,
            modifier = Modifier.testTag(ResetPasswordTestTags.SUBMIT_ACTION),
        )
        SecondaryActionButton(
            text = stringResource(R.string.reset_password_send_new_code),
            onClick = onSendNewCode,
            modifier = Modifier.testTag(ResetPasswordTestTags.SEND_NEW_CODE_ACTION),
        )
    }
}

@Composable
private fun CodeField(
    state: ResetPasswordViewState,
    codeState: TextFieldState,
    focus: ResetFieldFocus,
    onSubmit: () -> Unit,
) {
    GdsTextInput(
        state = codeState,
        label = stringResource(R.string.reset_password_code_label),
        testTag = ResetPasswordTestTags.CODE_FIELD,
        errorMessage = state.errorFor(ResetField.Code),
        keyboardOptions =
            KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
                autoCorrectEnabled = false,
            ),
        onKeyboardAction = KeyboardActionHandler { onSubmit() },
        focusRequester = focus.of(ResetField.Code),
        enabled = !state.isLoading,
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 390, heightDp = 844)
@Composable
fun ResetPasswordCodePreview() {
    MmoTheme {
        ResetPasswordCodeContent(
            state = ResetPasswordViewState(step = ResetStep.CodeEntry, codeLifetimeMinutes = 5),
            codeState = remember { TextFieldState() },
            chrome = ResetChrome("en") {},
            onSubmit = {},
            onSendNewCode = {},
        )
    }
}
