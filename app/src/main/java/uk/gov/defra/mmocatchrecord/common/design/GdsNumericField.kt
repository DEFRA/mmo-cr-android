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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType

/** Whether a [GdsNumericField] accepts whole numbers only, or decimals (a single '.' permitted). */
enum class GdsNumericFieldKind {
    Integer,
    Decimal,
}

/**
 * A single full-width numeric text input following the same GDS-style outlined-field visual language as
 * [GdsDateInput]'s per-part fields, generalised for reuse by any "enter a number" field — gear
 * measurements (mesh size, number of trawl nets) and shot counts — rather than being tied to date entry.
 * [GdsDateInput]'s own part field stays private/date-specific (fixed width, 2/4-digit max length), so this
 * is intentionally a separate, general-purpose component rather than an extraction of it.
 */
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
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(filterNumericInput(it, kind)) },
            singleLine = true,
            isError = errorText != null,
            shape = RectangleShape,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = imeAction),
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
    }
}

private fun filterNumericInput(
    raw: String,
    kind: GdsNumericFieldKind,
): String =
    when (kind) {
        GdsNumericFieldKind.Integer -> raw.filter(Char::isDigit)
        GdsNumericFieldKind.Decimal -> {
            var seenDecimalPoint = false
            raw.filter { char ->
                when {
                    char.isDigit() -> true
                    char == '.' && !seenDecimalPoint -> {
                        seenDecimalPoint = true
                        true
                    }
                    else -> false
                }
            }
        }
    }
