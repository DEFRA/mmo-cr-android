@file:Suppress("detekt.FunctionNaming")

package uk.gov.defra.mmocatchrecord.common.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
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
    labelWeight: FontWeight = FontWeight.Bold,
) {
    val errorAnnouncement = gdsErrorAnnouncement(errorMessage)
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
            labelWeight = labelWeight,
        )
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            GdsSecureField(
                state = state,
                enabled = enabled,
                isError = errorMessage != null,
                passwordVisible = passwordVisible,
                keyboardOptions = keyboardOptions,
                onKeyboardAction = onKeyboardAction,
                modifier =
                    Modifier
                        .weight(1f)
                        .focusRequesterIfPresent(focusRequester)
                        .testTag(testTag)
                        .semantics {
                            contentDescription = fieldDescription(label, hint)
                            if (errorAnnouncement != null) error(errorAnnouncement)
                            this.contentType = contentType
                        },
            )
            Box(Modifier.fillMaxHeight()) {
                PasswordVisibilityButton(
                    passwordVisible = passwordVisible,
                    spokenName = if (passwordVisible) labels.hideContentDescription else labels.showContentDescription,
                    testTag = "${testTag}_toggle",
                    enabled = enabled,
                    onClick = {
                        hasToggled = true
                        onVisibilityChange(!passwordVisible)
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
}

/** GOV.UK secure input drawn by [GdsFieldDecorationBox]. */
@Composable
@Suppress("LongParameterList")
private fun GdsSecureField(
    state: TextFieldState,
    enabled: Boolean,
    isError: Boolean,
    passwordVisible: Boolean,
    keyboardOptions: KeyboardOptions,
    onKeyboardAction: KeyboardActionHandler?,
    modifier: Modifier = Modifier,
) {
    BasicSecureTextField(
        state = state,
        enabled = enabled,
        textObfuscationMode = if (passwordVisible) TextObfuscationMode.Visible else TextObfuscationMode.Hidden,
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onKeyboardAction,
        textStyle = gdsFieldTextStyle(enabled),
        cursorBrush = GdsFieldCursorBrush,
        modifier = modifier,
        decorator = { innerTextField ->
            GdsFieldDecorationBox(
                innerTextField = innerTextField,
                isEmpty = state.text.isEmpty(),
                enabled = enabled,
                isError = isError,
            )
        },
    )
}

/** GOV.UK secondary button: grey with a darker bottom edge, turning focus-yellow with a black edge on focus. */
@Composable
private fun PasswordVisibilityButton(
    passwordVisible: Boolean,
    spokenName: String,
    testTag: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    GdsButtonFrame(
        onClick = onClick,
        enabled = enabled,
        horizontalPadding = Spacing.xs,
        colors =
            if (enabled) {
                GdsButtonColors(
                    fill = MmoColors.SecondaryButton,
                    pressedFill = MmoColors.SecondaryButtonPressed,
                    content = MmoColors.Text,
                    edge = MmoColors.SecondaryButtonShadow,
                )
            } else {
                GdsButtonColors(
                    fill = MmoColors.SecondaryButton,
                    pressedFill = MmoColors.SecondaryButton,
                    content = MmoColors.Grey1,
                )
            },
        modifier =
            Modifier
                .fillMaxHeight()
                .widthIn(min = TOGGLE_MIN_WIDTH)
                .testTag(testTag)
                .semantics { contentDescription = spokenName },
    ) { contentColor ->
        val show = stringResource(R.string.gds_password_show)
        val hide = stringResource(R.string.gds_password_hide)
        val style = MaterialTheme.typography.bodyLarge.copy(color = contentColor)
        // Both labels are laid out so the button keeps the wider one's width at any font size or language.
        Box(contentAlignment = Alignment.Center) {
            Text(text = if (passwordVisible) hide else show, style = style)
            Text(
                text = if (passwordVisible) show else hide,
                style = style,
                modifier = Modifier.alpha(0f).clearAndSetSemantics { },
            )
        }
    }
}

// The GOV.UK design's toggle width at default font size.
private val TOGGLE_MIN_WIDTH = 72.dp

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
