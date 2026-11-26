@file:Suppress("detekt.FunctionNaming")

package uk.gov.defra.mmocatchrecord.common.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import uk.gov.defra.mmocatchrecord.R

/** English, Welsh and 200% font-scale previews; API 36 because Studio cannot render SecureTextField at 37. */
@Preview(name = "English", showBackground = true, backgroundColor = 0xFFFFFFFF, apiLevel = 36)
@Preview(name = "Welsh", showBackground = true, backgroundColor = 0xFFFFFFFF, locale = "cy", apiLevel = 36)
@Preview(name = "200% font", showBackground = true, backgroundColor = 0xFFFFFFFF, fontScale = 2f, apiLevel = 36)
annotation class GdsFormPreviews

@GdsFormPreviews
@Composable
fun GdsTextInputPreview() {
    MmoTheme {
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            GdsTextInput(
                state = remember { TextFieldState() },
                label = "Email address",
                hint = "For example, name@example.com",
                testTag = "preview_email",
            )
            GdsTextInput(
                state = remember { TextFieldState("name@") },
                label = "Email address",
                errorMessage = "Enter an email address in the correct format",
                testTag = "preview_email_error",
            )
        }
    }
}

@GdsFormPreviews
@Composable
fun GdsPasswordInputPreview() {
    MmoTheme {
        var visible by remember { mutableStateOf(false) }
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            GdsPasswordInput(
                state = remember { TextFieldState("Passw0rd!") },
                label = "New password",
                hint = "At least 8 characters",
                testTag = "preview_password",
                passwordVisible = visible,
                onVisibilityChange = { visible = it },
                labels = previewPasswordLabels(),
            )
            GdsPasswordInput(
                state = remember { TextFieldState("abc") },
                label = "Confirm new password",
                errorMessage = "Passwords do not match",
                testTag = "preview_password_error",
                passwordVisible = false,
                onVisibilityChange = {},
                labels = previewPasswordLabels(),
            )
        }
    }
}

@GdsFormPreviews
@Composable
fun GdsErrorSummaryPreview() {
    MmoTheme {
        GdsErrorSummary(
            items =
                listOf(
                    GdsErrorSummaryItem("Enter an email address", onClick = {}),
                    GdsErrorSummaryItem("Passwords do not match", onClick = {}),
                ),
            focusRequester = remember { FocusRequester() },
            testTag = "preview_error_summary",
            modifier = Modifier.padding(Spacing.m),
        )
    }
}

@GdsFormPreviews
@Composable
fun GdsOfflineNoticePreview() {
    MmoTheme {
        GdsOfflineNotice(
            actionLabel = "Try again",
            onAction = {},
            modifier = Modifier.padding(Spacing.m),
        )
    }
}

@Composable
private fun previewPasswordLabels() =
    GdsPasswordInputLabels(
        showContentDescription = "${stringResource(R.string.gds_password_show)} new password",
        hideContentDescription = "${stringResource(R.string.gds_password_hide)} new password",
        visibleAnnouncement = "Password shown",
        hiddenAnnouncement = "Password hidden",
    )
