@file:Suppress("detekt.FunctionNaming", "detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus

object DepartureDateScreenTestTags {
    const val SCREEN = "departure_date_screen"
    const val ERROR_SUMMARY = "departure_date_error_summary"
    const val DAY_FIELD = "departure_date_day_field"
    const val MONTH_FIELD = "departure_date_month_field"
    const val YEAR_FIELD = "departure_date_year_field"
    const val SAVE_ACTION = "departure_date_save_action"
    const val ERROR_MESSAGE = "departure_date_error_message"
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
    CatchRecordWizardScaffold(
        screenTestTag = DepartureDateScreenTestTags.SCREEN,
        title = stringResource(R.string.departure_date_title),
        onBack = onBack,
        modifier = modifier,
    ) {
        when (val status = state.status) {
            UiStatus.Idle, UiStatus.Loading -> WizardLoadingState()
            is UiStatus.Error -> WizardErrorState(status.message, DepartureDateScreenTestTags.ERROR_MESSAGE)
            is UiStatus.Content ->
                WizardDateStepContent(
                    initialDate = status.value.departureDate,
                    testTags =
                        WizardDateStepTestTags(
                            summary = DepartureDateScreenTestTags.ERROR_SUMMARY,
                            dayField = DepartureDateScreenTestTags.DAY_FIELD,
                            monthField = DepartureDateScreenTestTags.MONTH_FIELD,
                            yearField = DepartureDateScreenTestTags.YEAR_FIELD,
                            saveAction = DepartureDateScreenTestTags.SAVE_ACTION,
                        ),
                    onSubmit = { date ->
                        viewModel.dispatch(
                            CatchRecordFlowEvent.SaveAndContinue(
                                status.value.copy(departureDate = date),
                                WizardStep.ReturnDate,
                            ),
                        )
                        onNavigate(WizardStep.ReturnDate)
                    },
                )
        }
    }
}
