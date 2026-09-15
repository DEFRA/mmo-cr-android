@file:Suppress("detekt.FunctionNaming", "detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus

object ReturnPortScreenTestTags {
    const val SCREEN = "return_port_screen"
    const val FAVOURITE_OPTION_PREFIX = "return_port_favourite_option"
    const val AUTOCOMPLETE_FIELD = "return_port_autocomplete_field"
    const val LIVE_REGION = "return_port_live_region"
    const val SUGGESTION_PREFIX = "return_port_suggestion"
    const val NO_MATCHES = "return_port_no_matches"
    const val ADD_PORT_ACTION = "return_port_add_port_action"
    const val SAVE_ACTION = "return_port_save_action"
    const val ERROR_MESSAGE = "return_port_error_message"
}

@Suppress("FunctionNaming")
@Composable
fun ReturnPortScreen(
    viewModel: CatchRecordFlowViewModel,
    onNavigate: (WizardStep) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CatchRecordWizardScaffold(
        screenTestTag = ReturnPortScreenTestTags.SCREEN,
        title = stringResource(R.string.return_port_title),
        onBack = onBack,
        modifier = modifier,
    ) {
        when (val status = state.status) {
            UiStatus.Idle, UiStatus.Loading -> WizardLoadingState()
            is UiStatus.Error -> WizardErrorState(status.message, ReturnPortScreenTestTags.ERROR_MESSAGE)
            is UiStatus.Content ->
                PortSelectionStepContent(
                    initialSelection = status.value.returnPort,
                    allPorts = state.ports,
                    favouritePorts = state.previouslyUsedPorts,
                    autocompleteLabel = stringResource(R.string.return_port_autocomplete_label),
                    autocompleteFieldTag = ReturnPortScreenTestTags.AUTOCOMPLETE_FIELD,
                    liveRegionTag = ReturnPortScreenTestTags.LIVE_REGION,
                    suggestionPrefix = ReturnPortScreenTestTags.SUGGESTION_PREFIX,
                    noMatchesTag = ReturnPortScreenTestTags.NO_MATCHES,
                    optionPrefix = ReturnPortScreenTestTags.FAVOURITE_OPTION_PREFIX,
                    addPortTag = ReturnPortScreenTestTags.ADD_PORT_ACTION,
                    saveTag = ReturnPortScreenTestTags.SAVE_ACTION,
                    errorTag = ReturnPortScreenTestTags.ERROR_MESSAGE,
                    onSubmit = { selection ->
                        viewModel.dispatch(
                            CatchRecordFlowEvent.SaveAndContinue(
                                status.value.copy(returnPort = selection),
                                WizardStep.GearSearch,
                            ),
                        )
                        onNavigate(WizardStep.GearSearch)
                    },
                )
        }
    }
}
