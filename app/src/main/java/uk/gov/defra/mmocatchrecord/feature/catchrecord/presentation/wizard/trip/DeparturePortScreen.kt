@file:Suppress("detekt.FunctionNaming", "detekt.LongParameterList", "detekt.LongMethod", "detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.common.design.AppLanguageProvider
import uk.gov.defra.mmocatchrecord.common.design.GdsAutocompleteField
import uk.gov.defra.mmocatchrecord.common.design.GdsAutocompleteOption
import uk.gov.defra.mmocatchrecord.common.design.GdsRadioGroup
import uk.gov.defra.mmocatchrecord.common.design.GdsRadioOption
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.common.design.PrimaryActionButton
import uk.gov.defra.mmocatchrecord.common.design.SecondaryActionButton
import uk.gov.defra.mmocatchrecord.common.design.Spacing
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelection
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelectionMode
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Port
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowEvent
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowViewModel
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowViewState
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordWizardScaffold
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.DeparturePortEntryMode
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardErrorState
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardLoadingState
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardStep

object DeparturePortScreenTestTags {
    const val SCREEN = "departure_port_screen"
    const val SAME_PORT_OPTION_PREFIX = "departure_port_same_port_option"
    const val FAVOURITE_OPTION_PREFIX = "departure_port_favourite_option"
    const val AUTOCOMPLETE_FIELD = "departure_port_autocomplete_field"
    const val LIVE_REGION = "departure_port_live_region"
    const val SUGGESTION_PREFIX = "departure_port_suggestion"
    const val NO_MATCHES = "departure_port_no_matches"
    const val ADD_PORT_ACTION = "departure_port_add_port_action"
    const val SAVE_ACTION = "departure_port_save_action"
    const val ERROR_MESSAGE = "departure_port_error_message"
}

@Suppress("FunctionNaming")
@Composable
fun DeparturePortScreen(
    viewModel: CatchRecordFlowViewModel,
    onNavigate: (WizardStep) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DeparturePortScreen(
        state = state,
        onSamePortAccepted = {
            viewModel.dispatch(CatchRecordFlowEvent.SamePortShortcutAccepted)
            onNavigate(WizardStep.GearSearch)
        },
        onSamePortDeclined = { viewModel.dispatch(CatchRecordFlowEvent.SamePortShortcutDeclined) },
        onSubmit = { updatedDraft ->
            viewModel.dispatch(CatchRecordFlowEvent.SaveAndContinue(updatedDraft, WizardStep.ReturnPort))
            onNavigate(WizardStep.ReturnPort)
        },
        onBack = onBack,
        modifier = modifier,
    )
}

@Suppress("FunctionNaming")
@Composable
private fun DeparturePortScreen(
    state: CatchRecordFlowViewState,
    onSamePortAccepted: () -> Unit,
    onSamePortDeclined: () -> Unit,
    onSubmit: (CatchRecordDraft) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CatchRecordWizardScaffold(
        screenTestTag = DeparturePortScreenTestTags.SCREEN,
        title = stringResource(R.string.departure_port_title),
        onBack = onBack,
        modifier = modifier,
    ) {
        when (val status = state.status) {
            UiStatus.Idle, UiStatus.Loading -> WizardLoadingState()
            is UiStatus.Error -> WizardErrorState(status.message, DeparturePortScreenTestTags.ERROR_MESSAGE)
            is UiStatus.Content ->
                DeparturePortScreenContent(
                    draft = status.value,
                    allPorts = state.ports,
                    favouritePorts = state.previouslyUsedPorts,
                    entryMode = state.departurePortEntryMode,
                    samePortCandidate = state.samePortCandidate,
                    onSamePortAccepted = onSamePortAccepted,
                    onSamePortDeclined = onSamePortDeclined,
                    onSubmit = onSubmit,
                )
        }
    }
}

@Suppress("FunctionNaming", "LongParameterList")
@Composable
fun DeparturePortScreenContent(
    draft: CatchRecordDraft,
    allPorts: List<Port>,
    favouritePorts: List<Port>,
    entryMode: DeparturePortEntryMode,
    samePortCandidate: Port?,
    onSamePortAccepted: () -> Unit,
    onSamePortDeclined: () -> Unit,
    onSubmit: (CatchRecordDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (entryMode == DeparturePortEntryMode.SamePortShortcut && samePortCandidate != null) {
        DepartureSamePortShortcutContent(
            portName = samePortCandidate.name,
            onSamePortAccepted = onSamePortAccepted,
            onSamePortDeclined = onSamePortDeclined,
            modifier = modifier,
        )
        return
    }

    PortSelectionStepContent(
        modifier = modifier,
        initialSelection = draft.departurePort,
        allPorts = allPorts,
        favouritePorts = favouritePorts,
        autocompleteLabel = stringResource(R.string.departure_port_autocomplete_label),
        autocompleteFieldTag = DeparturePortScreenTestTags.AUTOCOMPLETE_FIELD,
        liveRegionTag = DeparturePortScreenTestTags.LIVE_REGION,
        suggestionPrefix = DeparturePortScreenTestTags.SUGGESTION_PREFIX,
        noMatchesTag = DeparturePortScreenTestTags.NO_MATCHES,
        optionPrefix = DeparturePortScreenTestTags.FAVOURITE_OPTION_PREFIX,
        addPortTag = DeparturePortScreenTestTags.ADD_PORT_ACTION,
        saveTag = DeparturePortScreenTestTags.SAVE_ACTION,
        errorTag = DeparturePortScreenTestTags.ERROR_MESSAGE,
        onSubmit = { selection -> onSubmit(draft.copy(departurePort = selection)) },
    )
}

@Suppress("FunctionNaming")
@Composable
private fun DepartureSamePortShortcutContent(
    portName: String,
    onSamePortAccepted: () -> Unit,
    onSamePortDeclined: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedOptionId by rememberSaveable { mutableStateOf<String?>(null) }
    var showError by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        GdsRadioGroup(
            options =
                listOf(
                    GdsRadioOption("yes", stringResource(R.string.yes)),
                    GdsRadioOption("no", stringResource(R.string.no)),
                ),
            selectedOptionId = selectedOptionId,
            onOptionSelected = {
                selectedOptionId = it
                showError = false
            },
            optionTestTagPrefix = DeparturePortScreenTestTags.SAME_PORT_OPTION_PREFIX,
        )
        Text(
            text = stringResource(R.string.same_port_shortcut_question, portName),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (showError) {
            Text(
                text = stringResource(R.string.same_port_shortcut_error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(DeparturePortScreenTestTags.ERROR_MESSAGE),
            )
        }
        PrimaryActionButton(
            text = stringResource(R.string.save_and_continue),
            onClick = {
                when (selectedOptionId) {
                    "yes" -> onSamePortAccepted()
                    "no" -> onSamePortDeclined()
                    else -> showError = true
                }
            },
            modifier = Modifier.testTag(DeparturePortScreenTestTags.SAVE_ACTION),
        )
    }
}

@Suppress("FunctionNaming", "LongParameterList", "LongMethod")
@Composable
fun PortSelectionStepContent(
    initialSelection: PortSelection?,
    allPorts: List<Port>,
    favouritePorts: List<Port>,
    autocompleteLabel: String,
    autocompleteFieldTag: String,
    liveRegionTag: String,
    suggestionPrefix: String,
    noMatchesTag: String,
    optionPrefix: String,
    addPortTag: String,
    saveTag: String,
    errorTag: String,
    onSubmit: (PortSelection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val initialPortName =
        remember(initialSelection, allPorts) {
            allPorts
                .firstOrNull {
                    it.id ==
                        initialSelection?.portId
                }?.name
                .orEmpty()
        }
    var showSearch by rememberSaveable(initialSelection?.selectionMode, favouritePorts.isEmpty()) {
        mutableStateOf(favouritePorts.isEmpty() || initialSelection?.selectionMode == PortSelectionMode.FirstTime)
    }
    var selectedFavouriteId by rememberSaveable {
        mutableStateOf(
            initialSelection
                ?.takeIf {
                    it.selectionMode ==
                        PortSelectionMode.Favourite
                }?.portId,
        )
    }
    var selectedSearchId by rememberSaveable {
        mutableStateOf(
            initialSelection
                ?.takeIf {
                    it.selectionMode ==
                        PortSelectionMode.FirstTime
                }?.portId,
        )
    }
    var searchQuery by rememberSaveable { mutableStateOf(initialPortName) }
    var showError by rememberSaveable { mutableStateOf(false) }
    val suggestions = remember(searchQuery, allPorts) { PortSearch.filterSuggestions(searchQuery, allPorts) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        if (!showSearch && favouritePorts.isNotEmpty()) {
            GdsRadioGroup(
                options = favouritePorts.map { GdsRadioOption(it.id, it.name) },
                selectedOptionId = selectedFavouriteId,
                onOptionSelected = {
                    selectedFavouriteId = it
                    showError = false
                },
                optionTestTagPrefix = optionPrefix,
            )
            SecondaryActionButton(
                text = stringResource(R.string.add_port),
                onClick = {
                    showSearch = true
                    showError = false
                },
                modifier = Modifier.testTag(addPortTag),
            )
        } else {
            GdsAutocompleteField(
                label = autocompleteLabel,
                value = searchQuery,
                options = suggestions.map { GdsAutocompleteOption(it.id, it.name) },
                onValueChange = {
                    searchQuery = it
                    selectedSearchId = allPorts.firstOrNull { port -> port.name.equals(it, ignoreCase = true) }?.id
                    showError = false
                },
                onOptionSelected = {
                    searchQuery = it.label
                    selectedSearchId = it.id
                    showError = false
                },
                fieldTestTag = autocompleteFieldTag,
                liveRegionTestTag = liveRegionTag,
                suggestionTestTagPrefix = suggestionPrefix,
                noMatchesTestTag = noMatchesTag,
            )
        }
        if (showError) {
            Text(
                text = stringResource(R.string.port_selection_error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(errorTag),
            )
        }
        PrimaryActionButton(
            text = stringResource(R.string.save_and_continue),
            onClick = {
                val selection =
                    if (showSearch) {
                        selectedSearchId?.let { PortSelection(it, PortSelectionMode.FirstTime) }
                    } else {
                        selectedFavouriteId?.let { PortSelection(it, PortSelectionMode.Favourite) }
                    }
                if (selection == null) {
                    showError = true
                } else {
                    onSubmit(selection)
                }
            },
            modifier = Modifier.testTag(saveTag),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Suppress("FunctionNaming")
@Composable
fun DeparturePortScreen_SearchPreview() {
    val samplePorts =
        listOf(
            Port("port-hastings", "Hastings", "AREA-HASTINGS"),
            Port("port-dover", "Dover", "AREA-DOVER"),
        )
    val sampleDraft =
        CatchRecordDraft(
            id = "draft-1",
            vesselId = "vessel-1",
            modifiedAtEpochMillis = 1605830400000L,
            status = DraftStatus.Draft,
        )
    val state =
        CatchRecordFlowViewState(
            status = UiStatus.Content(sampleDraft),
            ports = samplePorts,
            departurePortEntryMode = DeparturePortEntryMode.Search,
        )
    MmoTheme {
        AppLanguageProvider(language = "en") {
            DeparturePortScreen(
                state = state,
                onSamePortAccepted = {},
                onSamePortDeclined = {},
                onSubmit = {},
                onBack = {},
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Suppress("FunctionNaming")
@Composable
fun DeparturePortScreen_SamePortShortcutPreview() {
    val samplePorts =
        listOf(
            Port("port-hastings", "Hastings", "AREA-HASTINGS"),
            Port("port-dover", "Dover", "AREA-DOVER"),
        )
    val sampleDraft =
        CatchRecordDraft(
            id = "draft-1",
            vesselId = "vessel-1",
            modifiedAtEpochMillis = 1605830400000L,
            status = DraftStatus.Draft,
        )
    val state =
        CatchRecordFlowViewState(
            status = UiStatus.Content(sampleDraft),
            ports = samplePorts,
            previouslyUsedPorts = listOf(samplePorts.first()),
            departurePortEntryMode = DeparturePortEntryMode.SamePortShortcut,
            samePortCandidate = samplePorts.first(),
        )
    MmoTheme {
        AppLanguageProvider(language = "en") {
            DeparturePortScreen(
                state = state,
                onSamePortAccepted = {},
                onSamePortDeclined = {},
                onSubmit = {},
                onBack = {},
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Suppress("FunctionNaming")
@Composable
fun DeparturePortScreen_FavouritesPreview() {
    val samplePorts =
        listOf(
            Port("port-hastings", "Hastings", "AREA-HASTINGS"),
            Port("port-dover", "Dover", "AREA-DOVER"),
        )
    val sampleDraft =
        CatchRecordDraft(
            id = "draft-1",
            vesselId = "vessel-1",
            modifiedAtEpochMillis = 1605830400000L,
            status = DraftStatus.Draft,
        )
    val state =
        CatchRecordFlowViewState(
            status = UiStatus.Content(sampleDraft),
            ports = samplePorts,
            previouslyUsedPorts = samplePorts,
            departurePortEntryMode = DeparturePortEntryMode.FavouriteList,
        )
    MmoTheme {
        AppLanguageProvider(language = "en") {
            DeparturePortScreen(
                state = state,
                onSamePortAccepted = {},
                onSamePortDeclined = {},
                onSubmit = {},
                onBack = {},
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Suppress("FunctionNaming")
@Composable
fun DeparturePortScreen_ErrorPreview() {
    val state =
        CatchRecordFlowViewState(
            status = UiStatus.Error("Unable to load ports. Please try again later.", isRetryable = true),
        )
    MmoTheme {
        AppLanguageProvider(language = "en") {
            DeparturePortScreen(
                state = state,
                onSamePortAccepted = {},
                onSamePortDeclined = {},
                onSubmit = {},
                onBack = {},
            )
        }
    }
}
