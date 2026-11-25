@file:Suppress("detekt.FunctionNaming")

package uk.gov.defra.mmocatchrecord.feature.passwordreset.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.common.design.GdsResultBanner
import uk.gov.defra.mmocatchrecord.common.design.GdsResultBannerVariant
import uk.gov.defra.mmocatchrecord.common.design.MmoColors
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.common.design.PrimaryActionButton

/** Step 4: confirmation (AC17). No automatic sign-in: the user returns to Sign in with [onSignIn] or Back. */
@Composable
fun PasswordResetSuccessContent(
    state: ResetPasswordViewState,
    chrome: ResetChrome,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    actions: ResetScreenActions = ResetScreenActions(onBack = onSignIn),
) {
    val title = stringResource(R.string.reset_password_success_title)
    ResetPasswordScaffold(
        screenTestTag = ResetPasswordTestTags.SUCCESS_SCREEN,
        title = title,
        state = state,
        chrome = chrome,
        actions = actions,
        modifier = modifier,
        // The banner is the page heading, so the shared heading is not repeated above it.
        showHeading = false,
    ) {
        GdsResultBanner(
            variant = GdsResultBannerVariant.Success,
            title = title,
            testTag = ResetPasswordTestTags.SUCCESS_BANNER,
        )
        Text(
            text = stringResource(R.string.reset_password_success_body),
            style = MaterialTheme.typography.bodyLarge.copy(color = MmoColors.Text),
            modifier = Modifier.testTag(ResetPasswordTestTags.GUIDANCE),
        )
        PrimaryActionButton(
            text = stringResource(R.string.signin_title),
            onClick = onSignIn,
            modifier = Modifier.testTag(ResetPasswordTestTags.SIGN_IN_ACTION),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun PasswordResetSuccessPreview() {
    MmoTheme {
        PasswordResetSuccessContent(
            state = ResetPasswordViewState(step = ResetStep.Success),
            chrome = ResetChrome("en") {},
            onSignIn = {},
        )
    }
}
