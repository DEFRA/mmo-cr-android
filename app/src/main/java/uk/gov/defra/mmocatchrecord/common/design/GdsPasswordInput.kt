@file:Suppress("detekt.FunctionNaming")

package uk.gov.defra.mmocatchrecord.common.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import uk.gov.defra.mmocatchrecord.R

/**
 * Spoken Show/Hide names and the polite state announcements for one [GdsPasswordInput]. Each field has its own
 * wording (e.g. "Show new password") and every spoken name contains the visible "Show"/"Hide" text (WCAG 2.5.3).
 */
data class GdsPasswordInputLabels(
    val showContentDescription: String,
    val hideContentDescription: String,
    val visibleAnnouncement: String,
    val hiddenAnnouncement: String,
)

/**
 * GOV.UK password input with an independent Show/Hide button; the password lives in a [TextFieldState],
 * never in UI state.
 */
@Composable
@Suppress("LongParameterList")
fun GdsPasswordInput(
    state: TextFieldState,
    label: String,
    testTag: String,
    passwordVisible: Boolean,
    onVisibilityChange: (Boolean) -> Unit,
    labels: GdsPasswordInputLabels,
    modifier: Modifier = Modifier,
    hint: String? = null,
    errorMessage: String? = null,
    contentType: ContentType = ContentType.NewPassword,
    keyboardOptions: KeyboardOptions =
        KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
            autoCorrectEnabled = false,
        ),
    onKeyboardAction: KeyboardActionHandler? = null,
    focusRequester: FocusRequester? = null,
    enabled: Boolean = true,
) {
    val errorPrefix = stringResource(R.string.gds_error_prefix)
    // Nothing is announced on first composition, only after the user has toggled this field.
    var hasToggled by remember { mutableStateOf(false) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        GdsFieldHeader(
            label = label,
            hint = hint,
            errorMessage = errorMessage,
            testTag = testTag,
        )
        Box {
            OutlinedSecureTextField(
                state = state,
                enabled = enabled,
                isError = errorMessage != null,
                textObfuscationMode = if (passwordVisible) TextObfuscationMode.Visible else TextObfuscationMode.Hidden,
                keyboardOptions = keyboardOptions,
                onKeyboardAction = onKeyboardAction,
                shape = RectangleShape,
                colors = gdsFieldColors(),
                trailingIcon = {
                    PasswordVisibilityButton(
                        passwordVisible = passwordVisible,
                        spokenName =
                            if (passwordVisible) labels.hideContentDescription else labels.showContentDescription,
                        testTag = "${testTag}_toggle",
                        onClick = {
                            hasToggled = true
                            onVisibilityChange(!passwordVisible)
                        },
                    )
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .focusRequesterIfPresent(focusRequester)
                        .testTag(testTag)
                        .semantics {
                            contentDescription = fieldDescription(label, hint)
                            if (errorMessage != null) error("$errorPrefix $errorMessage")
                            this.contentType = contentType
                        },
            )
            if (hasToggled) {
                VisuallyHiddenStatus(
                    text = if (passwordVisible) labels.visibleAnnouncement else labels.hiddenAnnouncement,
                    testTag = "${testTag}_status",
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
        }
    }
}

@Composable
private fun PasswordVisibilityButton(
    passwordVisible: Boolean,
    spokenName: String,
    testTag: String,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(contentColor = MmoColors.Link),
        modifier =
            Modifier
                .heightIn(min = Spacing.minTouchTarget)
                .testTag(testTag)
                .semantics {
                    role = Role.Button
                    contentDescription = spokenName
                },
    ) {
        Text(
            text = stringResource(if (passwordVisible) R.string.gds_password_hide else R.string.gds_password_show),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
        )
    }
}

/** A 1dp polite live region with no visible content: TalkBack speaks [text], so a state change is announced. */
@Composable
private fun VisuallyHiddenStatus(
    text: String,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(1.dp)
                .testTag(testTag)
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = text
                },
    )
}
