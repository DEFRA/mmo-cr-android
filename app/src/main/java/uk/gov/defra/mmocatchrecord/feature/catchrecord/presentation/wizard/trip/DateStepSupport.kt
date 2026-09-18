@file:Suppress("detekt.FunctionNaming", "detekt.LongMethod", "detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.common.design.GdsDateInput
import uk.gov.defra.mmocatchrecord.common.design.GdsDateInputValue
import uk.gov.defra.mmocatchrecord.common.design.PrimaryActionButton
import uk.gov.defra.mmocatchrecord.common.design.Spacing
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DmyDate
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardErrorSummary
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardErrorSummaryItem

data class WizardDateStepTestTags(
    val summary: String,
    val dayField: String,
    val monthField: String,
    val yearField: String,
    val saveAction: String,
)

@Suppress("FunctionNaming", "LongMethod")
@Composable
fun WizardDateStepContent(
    initialDate: DmyDate?,
    testTags: WizardDateStepTestTags,
    onSubmit: (DmyDate) -> Unit,
    modifier: Modifier = Modifier,
    departureDate: DmyDate? = null,
    isReturnDate: Boolean = false,
) {
    // Seeded from the flow ViewModel's current draft value (see DepartureDateScreen/ReturnDateScreen),
    // which is itself rehydrated from the encrypted Room draft on flow entry — so re-entering this step
    // (via Back, draft resume, or a future "Change" link) shows the previously saved value pre-filled
    // rather than blank fields. Day/month are zero-padded (e.g. "03") to match the GDS two-digit date
    // input convention.
    var day by rememberSaveable { mutableStateOf(initialDate?.day?.let { zeroPad(it) }.orEmpty()) }
    var month by rememberSaveable { mutableStateOf(initialDate?.month?.let { zeroPad(it) }.orEmpty()) }
    var year by rememberSaveable { mutableStateOf(initialDate?.year?.toString().orEmpty()) }
    var errors by remember { mutableStateOf<Map<DateInputField, DateInputError>>(emptyMap()) }
    var focusSummary by remember { mutableStateOf(false) }
    val summaryFocusRequester = remember { FocusRequester() }
    val dayFocusRequester = remember { FocusRequester() }
    val monthFocusRequester = remember { FocusRequester() }
    val yearFocusRequester = remember { FocusRequester() }

    LaunchedEffect(focusSummary) {
        if (focusSummary) {
            summaryFocusRequester.requestFocus()
            focusSummary = false
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        if (errors.isNotEmpty()) {
            WizardErrorSummary(
                title = stringResource(R.string.error_summary_title),
                items =
                    errors.map { (field, error) ->
                        WizardErrorSummaryItem(
                            message = dateErrorMessage(error),
                            onClick = {
                                when (field) {
                                    DateInputField.Day -> dayFocusRequester.requestFocus()
                                    DateInputField.Month -> monthFocusRequester.requestFocus()
                                    DateInputField.Year -> yearFocusRequester.requestFocus()
                                }
                            },
                        )
                    },
                focusRequester = summaryFocusRequester,
                testTag = testTags.summary,
            )
        }
        GdsDateInput(
            value = GdsDateInputValue(day = day, month = month, year = year),
            onValueChange = {
                day = it.day
                month = it.month
                year = it.year
                errors = emptyMap()
            },
            dayTestTag = testTags.dayField,
            monthTestTag = testTags.monthField,
            yearTestTag = testTags.yearField,
            dayError = errors[DateInputField.Day]?.let { dateErrorMessage(it) },
            monthError = errors[DateInputField.Month]?.let { dateErrorMessage(it) },
            yearError = errors[DateInputField.Year]?.let { dateErrorMessage(it) },
            dayFocusRequester = dayFocusRequester,
            monthFocusRequester = monthFocusRequester,
            yearFocusRequester = yearFocusRequester,
        )
        PrimaryActionButton(
            text = stringResource(R.string.save_and_continue),
            onClick = {
                val result =
                    WizardDateInputValidator.validate(
                        day = day,
                        month = month,
                        year = year,
                        departureDate = departureDate,
                        isReturnDate = isReturnDate,
                    )
                if (result.isValid) {
                    errors = emptyMap()
                    onSubmit(result.date!!)
                } else {
                    errors = result.errors
                    focusSummary = true
                }
            },
            modifier = Modifier.testTag(testTags.saveAction),
        )
    }
}

/** Zero-pads a day/month value to two digits (e.g. `3` -> `"03"`) to match the GDS date input convention. */
private fun zeroPad(value: Int): String = value.toString().padStart(2, '0')

@Suppress("FunctionNaming")
@Composable
private fun dateErrorMessage(error: DateInputError): String =
    when (error) {
        DateInputError.DayRequired -> stringResource(R.string.date_error_day_required)
        DateInputError.MonthRequired -> stringResource(R.string.date_error_month_required)
        DateInputError.YearRequired -> stringResource(R.string.date_error_year_required)
        DateInputError.DayNumeric -> stringResource(R.string.date_error_day_numeric)
        DateInputError.MonthNumeric -> stringResource(R.string.date_error_month_numeric)
        DateInputError.YearNumeric -> stringResource(R.string.date_error_year_numeric)
        DateInputError.InvalidDate -> stringResource(R.string.date_error_invalid_date)
        DateInputError.ReturnBeforeDeparture -> stringResource(R.string.date_error_return_before_departure)
    }
