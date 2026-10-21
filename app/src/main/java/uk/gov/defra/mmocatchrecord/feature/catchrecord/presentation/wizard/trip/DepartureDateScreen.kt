@file:Suppress("detekt.FunctionNaming", "detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.trip

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowEvent
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowViewModel
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordWizardScaffold
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardErrorState
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardLoadingState
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardStep
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardUnsavedChangesConfig
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.catchRecordReference

object DepartureDateScreenTestTags {
    const val SCREEN = "departure_date_screen"
    const val ERROR_SUMMARY = "departure_date_error_summary"
    const val MONTH_WHEEL = "departure_date_month_wheel"
    const val DAY_WHEEL = "departure_date_day_wheel"
    const val YEAR_WHEEL = "departure_date_year_wheel"
    const val OK_ACTION = "departure_date_ok_action"
    const val CANCEL_ACTION = "departure_date_cancel_action"
    const val CONFIRMED_DATE = "departure_date_confirmed_date"
    const val SAVE_ACTION = "departure_date_save_action"
    const val ERROR_MESSAGE = "departure_date_error_message"

    val stepTags =
        WizardDateStepTestTags(
            summary = ERROR_SUMMARY,
            monthWheel = MONTH_WHEEL,
            dayWheel = DAY_WHEEL,
            yearWheel = YEAR_WHEEL,
            okAction = OK_ACTION,
            cancelAction = CANCEL_ACTION,
            saveAction = SAVE_ACTION,
            confirmedDate = CONFIRMED_DATE,
        )
}

@Suppress("FunctionNaming")
@Composable
fun DepartureDateScreen(
    viewModel: CatchRecordFlowViewModel,
    onNavigate: (WizardStep) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var hasUnsavedChanges by rememberSaveable { mutableStateOf(false) }
    CatchRecordWizardScaffold(
        screenTestTag = DepartureDateScreenTestTags.SCREEN,
        title = stringResource(R.string.departure_date_title),
        onBack = onBack,
        modifier = modifier,
        referenceNumber = state.catchRecordReference,
        unsavedChanges = WizardUnsavedChangesConfig(hasUnsavedChanges = hasUnsavedChanges),
    ) {
        when (val status = state.status) {
            UiStatus.Idle, UiStatus.Loading -> WizardLoadingState()
            is UiStatus.Error ->
                WizardErrorState(
                    message = status.message,
                    testTag = DepartureDateScreenTestTags.ERROR_MESSAGE,
                    isRetryable = status.isRetryable,
                    onRetry = { viewModel.dispatch(CatchRecordFlowEvent.Retry) },
                )
            is UiStatus.Content ->
                @Suppress("AssignedValueIsNeverRead")
                WizardDateStepContent(
                    initialDate = status.value.departureDate,
                    testTags = DepartureDateScreenTestTags.stepTags,
                    onSubmit = { date ->
                        viewModel.dispatch(
                            CatchRecordFlowEvent.SaveAndContinue(
                                status.value.copy(departureDate = date),
                                WizardStep.ReturnDate,
                            ),
                        )
                        onNavigate(WizardStep.ReturnDate)
                    },
                    onDirtyChanged = { hasUnsavedChanges = it },
                )
        }
    }
}
