@file:Suppress("detekt.FunctionNaming", "detekt.LongParameterList", "detekt.LongMethod", "detekt.MaxLineLength")

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.common.design.AppLanguageProvider
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.common.design.PrimaryActionButton
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
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardUnsavedChangesConfig
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.catchRecordReference

object DeparturePortScreenTestTags {
    const val SCREEN = "departure_port_screen"
    const val FAVOURITE_OPTION_PREFIX = "departure_port_favourite_option"
    const val CONFIRM_OPTION_PREFIX = "departure_port_confirm_option"
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
    var hasUnsavedChanges by rememberSaveable { mutableStateOf(false) }
    @Suppress("AssignedValueIsNeverRead")
    DeparturePortScreen(
        state = state,
        onSubmit = { updatedDraft ->
            viewModel.dispatch(CatchRecordFlowEvent.SaveAndContinue(updatedDraft, WizardStep.ReturnPort))
            onNavigate(WizardStep.ReturnPort)
        },
        onRetry = { viewModel.dispatch(CatchRecordFlowEvent.Retry) },
        onBack = onBack,
        modifier = modifier,
        hasUnsavedChanges = hasUnsavedChanges,
        onDirtyChanged = { hasUnsavedChanges = it },
    )
}

@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun DeparturePortScreen(
    state: CatchRecordFlowViewState,
    onSubmit: (CatchRecordDraft) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    hasUnsavedChanges: Boolean = false,
    onDirtyChanged: (Boolean) -> Unit = {},
) {
    val favouritePorts = state.previouslyUsedPorts
    val initialDeparturePort = (state.status as? UiStatus.Content)?.value?.departurePort
    var showingPortConfirmation by rememberPortConfirmationVisible(favouritePorts, initialDeparturePort)
    var showingSearch by rememberPortSearchVisible(favouritePorts, initialDeparturePort)
    CatchRecordWizardScaffold(
        screenTestTag = DeparturePortScreenTestTags.SCREEN,
        title =
            portSelectionScaffoldTitle(
                showingPortConfirmation = showingPortConfirmation,
                favouritePorts = favouritePorts,
                showSearch = showingSearch,
                firstEntryTitleRes = R.string.departure_port_title,
                secondEntryTitleRes = R.string.second_entry_departure_port_title,
            ),
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
                    testTag = DeparturePortScreenTestTags.ERROR_MESSAGE,
                    isRetryable = status.isRetryable,
                    onRetry = onRetry,
                )
            is UiStatus.Content ->
                DeparturePortScreenContent(
                    draft = status.value,
                    allPorts = state.ports,
                    favouritePorts = favouritePorts,
                    onSubmit = onSubmit,
                    onDirtyChanged = onDirtyChanged,
                    onShowingPortConfirmationChanged = { showingPortConfirmation = it },
                    onShowSearchChanged = { showingSearch = it },
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
    onSubmit: (CatchRecordDraft) -> Unit,
    modifier: Modifier = Modifier,
    onDirtyChanged: (Boolean) -> Unit = {},
    onShowingPortConfirmationChanged: (Boolean) -> Unit = {},
    onShowSearchChanged: (Boolean) -> Unit = {},
) {
    PortSelectionStepContent(
        modifier = modifier,
        initialSelection = draft.departurePort,
        allPorts = allPorts,
        favouritePorts = favouritePorts,
        autocompletePlaceholder = stringResource(R.string.autocomplete_search_placeholder),
        autocompleteFieldTag = DeparturePortScreenTestTags.AUTOCOMPLETE_FIELD,
        liveRegionTag = DeparturePortScreenTestTags.LIVE_REGION,
        suggestionPrefix = DeparturePortScreenTestTags.SUGGESTION_PREFIX,
        noMatchesTag = DeparturePortScreenTestTags.NO_MATCHES,
        optionPrefix = DeparturePortScreenTestTags.FAVOURITE_OPTION_PREFIX,
        confirmOptionPrefix = DeparturePortScreenTestTags.CONFIRM_OPTION_PREFIX,
        addPortTag = DeparturePortScreenTestTags.ADD_PORT_ACTION,
        saveTag = DeparturePortScreenTestTags.SAVE_ACTION,
        errorTag = DeparturePortScreenTestTags.ERROR_MESSAGE,
        onSubmit = { selection -> onSubmit(draft.copy(departurePort = selection)) },
        onDirtyChanged = onDirtyChanged,
        onShowingPortConfirmationChanged = onShowingPortConfirmationChanged,
        onShowSearchChanged = onShowSearchChanged,
    )
}

@Suppress("FunctionNaming", "LongParameterList", "LongMethod")
@Composable
fun PortSelectionStepContent(
    initialSelection: PortSelection?,
    allPorts: List<Port>,
    favouritePorts: List<Port>,
    autocompletePlaceholder: String,
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
    confirmOptionPrefix: String = "",
    onDirtyChanged: (Boolean) -> Unit = {},
    onShowingPortConfirmationChanged: (Boolean) -> Unit = {},
    onShowSearchChanged: (Boolean) -> Unit = {},
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
        mutableStateOf(isPortSearchInitiallyShown(favouritePorts, initialSelection))
    }
    LaunchedEffect(showSearch) { onShowSearchChanged(showSearch) }
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
    // BR-XX (ADR 0014, Phase D): favourites radios commit on tap, so only the free-text search field
    // (see D3 scope) can hold genuinely uncommitted input.
    val isDirty = showSearch && !searchQuery.trim().equals(initialPortName.trim(), ignoreCase = true)
    LaunchedEffect(isDirty) { onDirtyChanged(isDirty) }
    // A single previously used port gets a Yes/No confirmation instead of a one-option list;
    // "No" reveals the normal search field.
    val confirmationPort = favouritePorts.singleOrNull()?.takeIf { !showSearch }
    val isSinglePortConfirmation = confirmationPort != null
    LaunchedEffect(isSinglePortConfirmation) { onShowingPortConfirmationChanged(isSinglePortConfirmation) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        if (confirmationPort != null) {
            SinglePortConfirmationOptions(
                question = stringResource(R.string.port_selection_confirm_title, confirmationPort.name),
                isSelectedYes = selectedFavouriteId == confirmationPort.id,
                onAnswered = { confirmedYes ->
                    if (confirmedYes) selectedFavouriteId = confirmationPort.id else showSearch = true
                    showError = false
                },
                optionTestTagPrefix = confirmOptionPrefix,
            )
        } else if (!showSearch && favouritePorts.isNotEmpty()) {
            PortSelectionFavouritesList(
                favouritePorts = favouritePorts,
                selectedFavouriteId = selectedFavouriteId,
                onOptionSelected = {
                    selectedFavouriteId = it
                    showError = false
                },
                onAddPort = {
                    showSearch = true
                    showError = false
                },
                optionTestTagPrefix = optionPrefix,
                addPortTag = addPortTag,
            )
        } else {
            PortSelectionSearchField(
                autocompletePlaceholder = autocompletePlaceholder,
                allPorts = allPorts,
                suggestions = suggestions,
                searchQuery = searchQuery,
                onQueryChanged = { query, matchedPortId ->
                    searchQuery = query
                    selectedSearchId = matchedPortId
                    showError = false
                },
                onSuggestionSelected = {
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
        // Mirrors TripTodayScreen: hide "Save and continue" until the Yes/No question is answered.
        if (confirmationPort == null || selectedFavouriteId == confirmationPort.id) {
            PrimaryActionButton(
                text = stringResource(R.string.save_and_continue),
                onClick = {
                    val selection = resolvePortSelection(showSearch, selectedSearchId, selectedFavouriteId)
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
                onSubmit = {},
                onBack = {},
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Suppress("FunctionNaming")
@Composable
fun DeparturePortScreenConfirmPreviousPortPreview() {
    val samplePorts = listOf(Port("port-hastings", "Hastings", "AREA-HASTINGS"))
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
                onSubmit = {},
                onBack = {},
            )
        }
    }
}
