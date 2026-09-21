@file:Suppress("detekt.FunctionNaming", "detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.trip

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.catchRecordReference

object ReturnDateScreenTestTags {
    const val SCREEN = "return_date_screen"
    const val ERROR_SUMMARY = "return_date_error_summary"
    const val DAY_FIELD = "return_date_day_field"
    const val MONTH_FIELD = "return_date_month_field"
    const val YEAR_FIELD = "return_date_year_field"
    const val SAVE_ACTION = "return_date_save_action"
    const val ERROR_MESSAGE = "return_date_error_message"
}

@Suppress("FunctionNaming")
@Composable
fun ReturnDateScreen(
    viewModel: CatchRecordFlowViewModel,
    onNavigate: (WizardStep) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CatchRecordWizardScaffold(
        screenTestTag = ReturnDateScreenTestTags.SCREEN,
        title = stringResource(R.string.return_date_title),
        onBack = onBack,
        modifier = modifier,
        referenceNumber = state.catchRecordReference,
    ) {
        when (val status = state.status) {
            UiStatus.Idle, UiStatus.Loading -> WizardLoadingState()
            is UiStatus.Error ->
                WizardErrorState(
                    message = status.message,
                    testTag = ReturnDateScreenTestTags.ERROR_MESSAGE,
                    isRetryable = status.isRetryable,
                    onRetry = { viewModel.dispatch(CatchRecordFlowEvent.Retry) },
                )
            is UiStatus.Content ->
                WizardDateStepContent(
                    initialDate = status.value.returnDate,
                    departureDate = status.value.departureDate,
                    isReturnDate = true,
                    testTags =
                        WizardDateStepTestTags(
                            summary = ReturnDateScreenTestTags.ERROR_SUMMARY,
                            dayField = ReturnDateScreenTestTags.DAY_FIELD,
                            monthField = ReturnDateScreenTestTags.MONTH_FIELD,
                            yearField = ReturnDateScreenTestTags.YEAR_FIELD,
                            saveAction = ReturnDateScreenTestTags.SAVE_ACTION,
                        ),
                    onSubmit = { date ->
                        viewModel.dispatch(
                            CatchRecordFlowEvent.SaveAndContinue(
                                status.value.copy(returnDate = date),
                                WizardStep.DeparturePort,
                            ),
                        )
                        onNavigate(WizardStep.DeparturePort)
                    },
                )
        }
    }
}
