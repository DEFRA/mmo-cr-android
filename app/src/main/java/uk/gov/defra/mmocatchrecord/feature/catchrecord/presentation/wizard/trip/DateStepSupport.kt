@file:Suppress("detekt.FunctionNaming", "detekt.LongMethod", "detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.common.design.GdsDateWheelPicker
import uk.gov.defra.mmocatchrecord.common.design.GdsDateWheelPickerTestTags
import uk.gov.defra.mmocatchrecord.common.design.MmoColors
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.common.design.PrimaryActionButton
import uk.gov.defra.mmocatchrecord.common.design.Spacing
import uk.gov.defra.mmocatchrecord.common.design.appLocale
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DmyDate
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardErrorSummary
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardErrorSummaryItem
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class WizardDateStepTestTags(
    val summary: String,
    val monthWheel: String,
    val dayWheel: String,
    val yearWheel: String,
    val okAction: String,
    val cancelAction: String,
    val saveAction: String,
    val confirmedDate: String,
)

@Suppress("FunctionNaming", "LongMethod", "LongParameterList")
@Composable
fun WizardDateStepContent(
    initialDate: DmyDate?,
    testTags: WizardDateStepTestTags,
    onSubmit: (DmyDate) -> Unit,
    modifier: Modifier = Modifier,
    departureDate: DmyDate? = null,
    isReturnDate: Boolean = false,
    onDirtyChanged: (Boolean) -> Unit = {},
    today: LocalDate = remember { LocalDate.now() },
) {
    // Seeded from the draft's saved value so re-entering the step shows it on the wheel and treats it as confirmed.
    val startDate = initialDate?.toLocalDateOrNull() ?: today
    var wheelEpochDay by rememberSaveable { mutableStateOf(startDate.toEpochDay()) }
    var confirmedEpochDay by rememberSaveable { mutableStateOf(initialDate?.toLocalDateOrNull()?.toEpochDay()) }
    var error by remember { mutableStateOf<DateInputError?>(null) }
    var focusSummary by remember { mutableStateOf(false) }
    val summaryFocusRequester = remember { FocusRequester() }
    val okFocusRequester = remember { FocusRequester() }

    val wheelDate = LocalDate.ofEpochDay(wheelEpochDay)
    val confirmedDate = confirmedEpochDay?.let(LocalDate::ofEpochDay)
    val yearRange = pickerYearRange(today, initialDate)

    // BR-XX (ADR 0014, Phase D): dirty if the confirmed date differs from the saved one, or the wheel has unconfirmed moves.
    val isDirty = confirmedDate?.toDmyDate() != initialDate || wheelDate != (confirmedDate ?: startDate)
    LaunchedEffect(isDirty) { onDirtyChanged(isDirty) }

    LaunchedEffect(focusSummary) {
        if (focusSummary) {
            summaryFocusRequester.requestFocus()
            focusSummary = false
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        val currentError = error
        if (currentError != null) {
            WizardErrorSummary(
                title = stringResource(R.string.error_summary_title),
                items =
                    listOf(
                        WizardErrorSummaryItem(
                            message = dateErrorMessage(currentError),
                            onClick = { okFocusRequester.requestFocus() },
                        ),
                    ),
                focusRequester = summaryFocusRequester,
                testTag = testTags.summary,
            )
            Text(
                text = dateErrorMessage(currentError),
                color = MmoColors.ErrorRed,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        @Suppress("AssignedValueIsNeverRead")
        GdsDateWheelPicker(
            value = wheelDate,
            onValueChange = {
                wheelEpochDay = it.toEpochDay()
                error = null
            },
            yearRange = yearRange,
            onConfirm = {
                confirmedEpochDay = wheelEpochDay
                error = null
            },
            onCancel = {
                wheelEpochDay = (confirmedDate ?: startDate).toEpochDay()
                error = null
            },
            testTags =
                GdsDateWheelPickerTestTags(
                    monthWheel = testTags.monthWheel,
                    dayWheel = testTags.dayWheel,
                    yearWheel = testTags.yearWheel,
                    okAction = testTags.okAction,
                    cancelAction = testTags.cancelAction,
                ),
            okFocusRequester = okFocusRequester,
        )
        if (confirmedDate != null) {
            val formatted = confirmedDate.format(DateTimeFormatter.ofPattern("d MMMM yyyy", appLocale()))
            Text(
                text = stringResource(R.string.date_picker_selected, formatted),
                style = MaterialTheme.typography.bodyLarge,
                modifier =
                    Modifier
                        .semantics { liveRegion = LiveRegionMode.Polite }
                        .testTag(testTags.confirmedDate),
            )
        }
        PrimaryActionButton(
            text = stringResource(R.string.save_and_continue),
            onClick = {
                val result =
                    WizardDateInputValidator.validate(
                        confirmedDate = confirmedDate?.toDmyDate(),
                        wheelDate = wheelDate.toDmyDate(),
                        departureDate = departureDate,
                        isReturnDate = isReturnDate,
                    )
                if (result.isValid) {
                    error = null
                    onSubmit(result.date!!)
                } else {
                    error = result.error
                    focusSummary = true
                }
            },
            modifier = Modifier.testTag(testTags.saveAction),
        )
    }
}

/** Last year to next year, widened to include a saved date outside that window. */
internal fun pickerYearRange(
    today: LocalDate,
    initialDate: DmyDate?,
): IntRange {
    val first = minOf(today.year - 1, initialDate?.year ?: Int.MAX_VALUE)
    val last = maxOf(today.year + 1, initialDate?.year ?: Int.MIN_VALUE)
    return first..last
}

private fun DmyDate.toLocalDateOrNull(): LocalDate? = runCatching { LocalDate.of(year, month, day) }.getOrNull()

private fun LocalDate.toDmyDate(): DmyDate = DmyDate(dayOfMonth, monthValue, year)

@Suppress("FunctionNaming")
@Composable
private fun dateErrorMessage(error: DateInputError): String =
    when (error) {
        DateInputError.NotConfirmed -> stringResource(R.string.date_error_not_confirmed)
        DateInputError.InvalidDate -> stringResource(R.string.date_error_invalid_date)
        DateInputError.ReturnBeforeDeparture -> stringResource(R.string.date_error_return_before_departure)
    }

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Suppress("FunctionNaming")
@Composable
fun WizardDateStepContentPreview() {
    MmoTheme {
        WizardDateStepContent(
            initialDate = DmyDate(1, 6, 2024),
            testTags =
                WizardDateStepTestTags(
                    summary = "summary",
                    monthWheel = "month",
                    dayWheel = "day",
                    yearWheel = "year",
                    okAction = "ok",
                    cancelAction = "cancel",
                    saveAction = "save",
                    confirmedDate = "confirmed",
                ),
            onSubmit = {},
        )
    }
}
