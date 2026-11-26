@file:Suppress("detekt.FunctionNaming")

package uk.gov.defra.mmocatchrecord.feature.passwordreset.presentation

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.DialogProperties
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.common.design.GdsPasswordInput
import uk.gov.defra.mmocatchrecord.common.design.GdsPasswordInputLabels
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.common.design.PrimaryActionButton
import uk.gov.defra.mmocatchrecord.common.design.SecureScreen
import uk.gov.defra.mmocatchrecord.common.design.Spacing
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.PasswordPolicy

/** Typed values and Show/Hide toggles for the two password fields, bundled to keep the signature short. */
data class NewPasswordFields(
    val newPassword: TextFieldState,
    val confirmPassword: TextFieldState,
    val onNewPasswordVisibilityChange: (Boolean) -> Unit,
    val onConfirmPasswordVisibilityChange: (Boolean) -> Unit,
    val policy: PasswordPolicy = PasswordPolicy(),
)

/**
 * Step 3: create a new password (AC11 to AC16). Hosted in [SecureScreen] so it is excluded from screenshots
 * and recents (Q11). Back asks "Cancel resetting your password?" before leaving, via [ResetScreenActions.onBack].
 */
@Composable
@Suppress("LongParameterList")
fun CreateNewPasswordContent(
    state: ResetPasswordViewState,
    fields: NewPasswordFields,
    chrome: ResetChrome,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    actions: ResetScreenActions = ResetScreenActions(),
) {
    SecureScreen()
    var confirmCancel by rememberSaveable { mutableStateOf(false) }
    val focus = remember { ResetFieldFocus() }
    ResetPasswordScaffold(
        screenTestTag = ResetPasswordTestTags.NEW_PASSWORD_SCREEN,
        title = stringResource(R.string.reset_password_new_title),
        state = state,
        chrome = chrome,
        actions = actions,
        modifier = modifier,
        focus = focus,
        firstField = ResetField.NewPassword,
        onBack = { confirmCancel = true },
    ) {
        NewPasswordInput(state, fields, focus)
        ConfirmPasswordInput(state, fields, focus, onSubmit)
        PrimaryActionButton(
            text = stringResource(R.string.reset_password_save),
            onClick = onSubmit,
            enabled = state.canSubmit,
            modifier = Modifier.testTag(ResetPasswordTestTags.SUBMIT_ACTION),
        )
    }
    if (confirmCancel) {
        CancelResetDialog(
            onStay = { confirmCancel = false },
            onLeave = {
                confirmCancel = false
                actions.onBack()
            },
        )
    }
}

@Composable
private fun NewPasswordInput(
    state: ResetPasswordViewState,
    fields: NewPasswordFields,
    focus: ResetFieldFocus,
) {
    GdsPasswordInput(
        state = fields.newPassword,
        label = stringResource(R.string.reset_password_new_label),
        hint =
            stringResource(
                R.string.reset_password_new_hint,
                fields.policy.minLength,
                fields.policy.maxLength,
                fields.policy.minCategories,
            ),
        testTag = ResetPasswordTestTags.NEW_PASSWORD_FIELD,
        passwordVisible = state.newPasswordVisible,
        onVisibilityChange = fields.onNewPasswordVisibilityChange,
        labels =
            GdsPasswordInputLabels(
                showContentDescription = stringResource(R.string.reset_password_new_show_description),
                hideContentDescription = stringResource(R.string.reset_password_new_hide_description),
                visibleAnnouncement = stringResource(R.string.reset_password_new_visible_announcement),
                hiddenAnnouncement = stringResource(R.string.reset_password_new_hidden_announcement),
            ),
        errorMessage = state.errorFor(ResetField.NewPassword),
        keyboardOptions =
            KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next,
                autoCorrectEnabled = false,
            ),
        focusRequester = focus.of(ResetField.NewPassword),
        enabled = !state.isLoading,
    )
}

@Composable
private fun ConfirmPasswordInput(
    state: ResetPasswordViewState,
    fields: NewPasswordFields,
    focus: ResetFieldFocus,
    onSubmit: () -> Unit,
) {
    GdsPasswordInput(
        state = fields.confirmPassword,
        label = stringResource(R.string.reset_password_confirm_label),
        testTag = ResetPasswordTestTags.CONFIRM_PASSWORD_FIELD,
        passwordVisible = state.confirmPasswordVisible,
        onVisibilityChange = fields.onConfirmPasswordVisibilityChange,
        labels =
            GdsPasswordInputLabels(
                showContentDescription = stringResource(R.string.reset_password_confirm_show_description),
                hideContentDescription = stringResource(R.string.reset_password_confirm_hide_description),
                visibleAnnouncement = stringResource(R.string.reset_password_confirm_visible_announcement),
                hiddenAnnouncement = stringResource(R.string.reset_password_confirm_hidden_announcement),
            ),
        errorMessage = state.errorFor(ResetField.ConfirmPassword),
        onKeyboardAction = KeyboardActionHandler { onSubmit() },
        focusRequester = focus.of(ResetField.ConfirmPassword),
        enabled = !state.isLoading,
    )
}

/** Same pattern as the wizard's unsaved-changes dialog, with reset-specific copy; Back/outside tap means stay. */
@Composable
private fun CancelResetDialog(
    onStay: () -> Unit,
    onLeave: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.testTag(ResetPasswordTestTags.CANCEL_DIALOG),
        onDismissRequest = onStay,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
        title = { Text(stringResource(R.string.reset_password_cancel_dialog_title)) },
        text = { Text(stringResource(R.string.reset_password_cancel_dialog_body)) },
        confirmButton = {
            TextButton(
                onClick = onLeave,
                modifier =
                    Modifier
                        .testTag(ResetPasswordTestTags.CANCEL_DIALOG_CONFIRM)
                        .heightIn(min = Spacing.minTouchTarget),
            ) { Text(stringResource(R.string.reset_password_cancel_dialog_confirm)) }
        },
        dismissButton = {
            TextButton(
                onClick = onStay,
                modifier =
                    Modifier
                        .testTag(ResetPasswordTestTags.CANCEL_DIALOG_DISMISS)
                        .heightIn(min = Spacing.minTouchTarget),
            ) { Text(stringResource(R.string.reset_password_cancel_dialog_dismiss)) }
        },
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 390, heightDp = 844)
@Composable
fun CreateNewPasswordPreview() {
    MmoTheme {
        CreateNewPasswordContent(
            state = ResetPasswordViewState(step = ResetStep.NewPassword),
            fields =
                NewPasswordFields(
                    newPassword = remember { TextFieldState() },
                    confirmPassword = remember { TextFieldState() },
                    onNewPasswordVisibilityChange = {},
                    onConfirmPasswordVisibilityChange = {},
                ),
            chrome = ResetChrome("en") {},
            onSubmit = {},
        )
    }
}
