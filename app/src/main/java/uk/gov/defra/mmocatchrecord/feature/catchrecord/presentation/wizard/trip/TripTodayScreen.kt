@file:Suppress("detekt.FunctionNaming", "detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.common.design.GdsRadioGroup
import uk.gov.defra.mmocatchrecord.common.design.GdsRadioOption
import uk.gov.defra.mmocatchrecord.common.design.PrimaryActionButton
import uk.gov.defra.mmocatchrecord.common.design.Spacing
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowEvent
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowViewModel
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordWizardScaffold
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardErrorState
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardLoadingState
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardStep
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.catchRecordReference

object TripTodayScreenTestTags {
    const val SCREEN = "trip_today_screen"
    const val OPTION_PREFIX = "trip_today_option"
    const val SAVE_ACTION = "trip_today_save_action"
    const val ERROR_MESSAGE = "trip_today_error_message"
}

@Suppress("FunctionNaming")
@Composable
fun TripTodayScreen(
    viewModel: CatchRecordFlowViewModel,
    onNavigate: (WizardStep) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CatchRecordWizardScaffold(
        screenTestTag = TripTodayScreenTestTags.SCREEN,
        title = stringResource(R.string.trip_today_title),
        onBack = onBack,
        modifier = modifier,
        referenceNumber = state.catchRecordReference,
    ) {
        when (val status = state.status) {
            UiStatus.Idle, UiStatus.Loading -> WizardLoadingState()
            is UiStatus.Error ->
                WizardErrorState(
                    message = status.message,
                    testTag = TripTodayScreenTestTags.ERROR_MESSAGE,
                    isRetryable = status.isRetryable,
                    onRetry = { viewModel.dispatch(CatchRecordFlowEvent.Retry) },
                )
            is UiStatus.Content ->
                TripTodayScreenContent(
                    initialValue = status.value.isTripToday,
                    onSubmit = { isTripToday ->
                        viewModel.dispatch(CatchRecordFlowEvent.TripTodayAnswered(isTripToday))
                        onNavigate(if (isTripToday) WizardStep.DeparturePort else WizardStep.DepartureDate)
                    },
                )
        }
    }
}

@Suppress("FunctionNaming")
@Composable
fun TripTodayScreenContent(
    initialValue: Boolean?,
    onSubmit: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedOptionId by rememberSaveable { mutableStateOf(initialValue?.toString()) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        GdsRadioGroup(
            options =
                listOf(
                    GdsRadioOption("true", stringResource(R.string.yes)),
                    GdsRadioOption("false", stringResource(R.string.no)),
                ),
            selectedOptionId = selectedOptionId,
            onOptionSelected = { selectedOptionId = it },
            optionTestTagPrefix = TripTodayScreenTestTags.OPTION_PREFIX,
        )
        when (selectedOptionId) {
            "true", "false" ->
                PrimaryActionButton(
                    text = stringResource(R.string.save_and_continue),
                    onClick = { onSubmit(selectedOptionId == "true") },
                    modifier = Modifier.testTag(TripTodayScreenTestTags.SAVE_ACTION),
                )
        }
    }
}
