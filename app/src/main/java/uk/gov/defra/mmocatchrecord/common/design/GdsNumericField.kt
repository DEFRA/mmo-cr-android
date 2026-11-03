@file:Suppress("detekt.FunctionNaming", "detekt.LongParameterList", "detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.common.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType

/** Whether a [GdsNumericField] accepts whole numbers only, or decimals (at most one '.' permitted). */
enum class GdsNumericFieldKind {
    Integer,
    Decimal,
}

/** A single full-width GDS-style outlined numeric text input (e.g. gear mesh size, number of trawl nets, shot counts). */
@Suppress("FunctionNaming", "LongParameterList")
@Composable
fun GdsNumericField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    kind: GdsNumericFieldKind = GdsNumericFieldKind.Integer,
    errorText: String? = null,
    imeAction: ImeAction = ImeAction.Done,
    maxDecimalPlaces: Int? = null,
    invalidInputMessage: String? = null,
) {
    var inputRejected by rememberSaveable { mutableStateOf(false) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            value = value,
            onValueChange = { candidate ->
                val accepted = filterNumericInput(value, candidate, kind, maxDecimalPlaces)
                inputRejected = accepted != candidate
                onValueChange(accepted)
            },
            singleLine = true,
            isError = errorText != null,
            shape = RectangleShape,
            // Decimal must use KeyboardType.Decimal, not Number, or most on-screen keyboards omit the "." key.
            keyboardOptions =
                KeyboardOptions(
                    keyboardType =
                        if (kind == GdsNumericFieldKind.Decimal) KeyboardType.Decimal else KeyboardType.Number,
                    imeAction = imeAction,
                ),
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = if (errorText == null) MmoColors.Text else MmoColors.ErrorRed,
                    unfocusedBorderColor = if (errorText == null) MmoColors.Text else MmoColors.ErrorRed,
                    focusedContainerColor = MmoColors.White,
                    unfocusedContainerColor = MmoColors.White,
                    cursorColor = MmoColors.Text,
                    focusedTextColor = MmoColors.Text,
                    unfocusedTextColor = MmoColors.Text,
                ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Spacing.minTouchTarget)
                    .testTag(testTag),
        )
        if (errorText != null) {
            Text(text = errorText, color = MmoColors.ErrorRed, style = MaterialTheme.typography.bodyMedium)
        }
        if (inputRejected && invalidInputMessage != null) {
            // Polite live region so TalkBack explains a blocked keystroke/paste without moving focus.
            Text(
                text = invalidInputMessage,
                color = MmoColors.Text,
                style = MaterialTheme.typography.bodyMedium,
                modifier =
                    Modifier
                        .testTag("${testTag}_$INVALID_INPUT_MESSAGE_TAG_SUFFIX")
                        .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** Suffix appended to a [GdsNumericField]'s testTag for its rejected-input message. */
const val INVALID_INPUT_MESSAGE_TAG_SUFFIX = "invalid_input_message"

// An invalid Decimal edit is rejected whole (keeps [current]) rather than char-stripped, so "1.2.3"
// or "5.55" can never be silently rewritten into a different weight such as "1.23" or "5.5".
internal fun filterNumericInput(
    current: String,
    raw: String,
    kind: GdsNumericFieldKind,
    maxDecimalPlaces: Int? = null,
): String =
    when (kind) {
        GdsNumericFieldKind.Integer -> raw.filter { it in '0'..'9' }
        GdsNumericFieldKind.Decimal -> {
            val fraction = maxDecimalPlaces?.let { "{0,$it}" } ?: "*"
            if (Regex("[0-9]*(\\.[0-9]$fraction)?").matches(raw)) raw else current
        }
    }
