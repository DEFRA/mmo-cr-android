@file:Suppress("detekt.FunctionNaming", "detekt.LongParameterList", "detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.common.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import uk.gov.defra.mmocatchrecord.R

data class GdsDateInputValue(
    val day: String = "",
    val month: String = "",
    val year: String = "",
)

@Suppress("FunctionNaming", "LongParameterList")
@Composable
fun GdsDateInput(
    value: GdsDateInputValue,
    onValueChange: (GdsDateInputValue) -> Unit,
    dayTestTag: String,
    monthTestTag: String,
    yearTestTag: String,
    modifier: Modifier = Modifier,
    dayError: String? = null,
    monthError: String? = null,
    yearError: String? = null,
    dayFocusRequester: FocusRequester? = null,
    monthFocusRequester: FocusRequester? = null,
    yearFocusRequester: FocusRequester? = null,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        DatePartField(stringResource(R.string.date_day_label), value.day, {
            onValueChange(value.copy(day = it))
        }, dayTestTag, dayError, 2, ImeAction.Next, 88.dp, dayFocusRequester)
        DatePartField(stringResource(R.string.date_month_label), value.month, {
            onValueChange(value.copy(month = it))
        }, monthTestTag, monthError, 2, ImeAction.Next, 88.dp, monthFocusRequester)
        DatePartField(stringResource(R.string.date_year_label), value.year, {
            onValueChange(value.copy(year = it))
        }, yearTestTag, yearError, 4, ImeAction.Done, 104.dp, yearFocusRequester)
    }
}

@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun DatePartField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    testTag: String,
    errorText: String?,
    maxLength: Int,
    imeAction: ImeAction,
    width: Dp,
    focusRequester: FocusRequester?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it.filter(Char::isDigit).take(maxLength)) },
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
                    .width(width)
                    .heightIn(min = Spacing.minTouchTarget)
                    .then(
                        if (focusRequester ==
                            null
                        ) {
                            Modifier
                        } else {
                            Modifier.focusRequester(focusRequester)
                        },
                    ).testTag(testTag),
        )
        if (errorText != null) {
            Text(text = errorText, color = MmoColors.ErrorRed, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
