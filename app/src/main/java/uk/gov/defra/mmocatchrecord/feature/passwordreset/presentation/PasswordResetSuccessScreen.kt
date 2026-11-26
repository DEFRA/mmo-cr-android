@file:Suppress("detekt.FunctionNaming")

package uk.gov.defra.mmocatchrecord.feature.passwordreset.presentation

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.common.design.PrimaryActionButton
import uk.gov.defra.mmocatchrecord.common.design.Spacing

/** Step 4: confirmation (AC17). No automatic sign-in: the user returns to Sign in with [onSignIn] or Back. */
@Composable
fun PasswordResetSuccessContent(
    state: ResetPasswordViewState,
    chrome: ResetChrome,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    actions: ResetScreenActions = ResetScreenActions(onBack = onSignIn),
) {
    ResetPasswordScaffold(
        screenTestTag = ResetPasswordTestTags.SUCCESS_SCREEN,
        title = stringResource(R.string.reset_password_success_title),
        state = state,
        chrome = chrome,
        actions = actions,
        modifier = modifier,
        showLogo = true,
        headingAlign = TextAlign.Center,
    ) {
        PrimaryActionButton(
            text = stringResource(R.string.signin_title),
            onClick = onSignIn,
            modifier = Modifier.padding(top = Spacing.xl).testTag(ResetPasswordTestTags.SIGN_IN_ACTION),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 390, heightDp = 844)
@Composable
fun PasswordResetSuccessPreview() {
    MmoTheme {
        PasswordResetSuccessContent(
            state = ResetPasswordViewState(step = ResetStep.Success),
            chrome = ResetChrome("en") {},
            onSignIn = {},
        )
    }
}
