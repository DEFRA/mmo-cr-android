@file:Suppress("detekt.FunctionNaming")

package uk.gov.defra.mmocatchrecord.common.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight

/**
 * GOV.UK text input with label, hint and inline error (icon + bold text + border, never colour alone).
 * Backed by a [TextFieldState] so typed text is never saved with UI state.
 */
@Composable
@Suppress("LongParameterList")
fun GdsTextInput(
    state: TextFieldState,
    label: String,
    testTag: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    errorMessage: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
    onKeyboardAction: KeyboardActionHandler? = null,
    contentType: ContentType? = null,
    focusRequester: FocusRequester? = null,
    enabled: Boolean = true,
) {
    val errorAnnouncement = gdsErrorAnnouncement(errorMessage)
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
        BasicTextField(
            state = state,
            enabled = enabled,
            lineLimits = TextFieldLineLimits.SingleLine,
            keyboardOptions = keyboardOptions,
            onKeyboardAction = onKeyboardAction,
            textStyle = gdsFieldTextStyle(enabled),
            cursorBrush = GdsFieldCursorBrush,
            decorator = { innerTextField ->
                GdsFieldDecorationBox(
                    innerTextField = innerTextField,
                    isEmpty = state.text.isEmpty(),
                    enabled = enabled,
                    isError = errorMessage != null,
                )
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequesterIfPresent(focusRequester)
                    .testTag(testTag)
                    .semantics {
                        // Compose has no labelFor: the visible label is spoken here, not announced twice.
                        contentDescription = fieldDescription(label, hint)
                        if (errorAnnouncement != null) error(errorAnnouncement)
                        if (contentType != null) this.contentType = contentType
                    },
        )
    }
}

internal fun Modifier.focusRequesterIfPresent(focusRequester: FocusRequester?): Modifier =
    if (focusRequester != null) focusRequester(focusRequester) else this

/** The name starts with the visible label (WCAG 2.5.3) and carries the hint after it. */
internal fun fieldDescription(
    label: String,
    hint: String?,
): String = if (hint.isNullOrBlank()) label else "$label. $hint"

/** Label, hint and error above a field; its texts are cleared from semantics because the field voices them. */
@Composable
internal fun GdsFieldHeader(
    label: String,
    hint: String?,
    errorMessage: String?,
    testTag: String,
    labelWeight: FontWeight = FontWeight.Bold,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = labelWeight, color = MmoColors.Text),
        modifier = Modifier.testTag("${testTag}_label").clearAndSetSemantics { },
    )
    if (!hint.isNullOrBlank()) {
        Text(
            text = hint,
            style = MaterialTheme.typography.bodyLarge.copy(color = MmoColors.SecondaryText),
            modifier = Modifier.testTag("${testTag}_hint").clearAndSetSemantics { },
        )
    }
    if (errorMessage != null) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag("${testTag}_error")
                    .clearAndSetSemantics { },
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.Top,
        ) {
            CustomWarningIcon(tint = MmoColors.ErrorRed, modifier = Modifier.size(Spacing.m))
            Text(
                text = errorMessage,
                style =
                    MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MmoColors.ErrorRed,
                    ),
            )
        }
    }
}
