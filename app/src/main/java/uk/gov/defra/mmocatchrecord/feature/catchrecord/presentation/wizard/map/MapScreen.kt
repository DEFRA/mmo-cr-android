@file:Suppress("detekt.FunctionNaming", "detekt.LongMethod", "detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import uk.gov.defra.mmocatchrecord.common.design.MmoColors
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.common.design.PrimaryActionButton
import uk.gov.defra.mmocatchrecord.common.design.SecondaryActionButton
import uk.gov.defra.mmocatchrecord.common.design.Spacing
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.MeasurementValue
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelection
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelectionMode
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldKeys
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Port
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.StatisticalSubRectangle
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowEvent
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowViewModel
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowViewState
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordWizardScaffold
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardErrorState
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardErrorSummary
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardErrorSummaryItem
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardLoadingState
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardStep
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.catchRecordReference
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.nextWizardStepForDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.trip.DeparturePortScreen
import uk.gov.defra.mmocatchrecord.mapdata.MapDataset

/** Which sub-screen of the [WizardStep.GearStatRectangle] step is currently shown — see [MapScreenContent]. */
enum class MapEntryMode {
    Grid,
    RadioList,
    Autocomplete,
}

/** Groups the current [MapEntryMode] sub-screen with its change callback — keeps composable parameter
 * counts within the kotlin:S107 limit instead of passing both separately. */
data class MapEntryModeState(
    val entryMode: MapEntryMode,
    val onEntryModeChange: (MapEntryMode) -> Unit,
)

/** Groups the offline map dataset's [UiStatus] with its retry callback — see [MapEntryModeState]. */
data class MapDataUiState(
    val mapStatus: UiStatus<MapDataset>,
    val onRetryMapData: () -> Unit,
)

/** Groups the nearby (departure-port-matched) and full/global statistical sub-rectangle lists passed to
 * [MapScreenContent]'s three sub-screens — see [MapEntryModeState]. */
data class StatRectangleOptions(
    val nearby: List<StatisticalSubRectangle>,
    val all: List<StatisticalSubRectangle>,
)

object MapScreenTestTags {
    const val SCREEN = "gear_stat_rectangle_screen"
    const val ERROR_SUMMARY = "gear_stat_rectangle_error_summary"
    const val ERROR_MESSAGE = "gear_stat_rectangle_error_message"
    const val MAP = "gear_stat_rectangle_map"
    const val MAP_ERROR = "gear_stat_rectangle_map_error"
    const val MAP_SELECTED_TEXT = "gear_stat_rectangle_map_selected_text"
    const val GRID_OTHER_ACTION = "gear_stat_rectangle_grid_other_action"
    const val GRID_SAVE_ACTION = "gear_stat_rectangle_grid_save_action"
    const val RADIO_OPTION_PREFIX = "gear_stat_rectangle_radio_option"
    const val RADIO_SAVE_ACTION = "gear_stat_rectangle_radio_save_action"
    const val AUTOCOMPLETE_FIELD = "gear_stat_rectangle_autocomplete_field"
    const val LIVE_REGION = "gear_stat_rectangle_live_region"
    const val SUGGESTION_PREFIX = "gear_stat_rectangle_suggestion"
    const val NO_MATCHES = "gear_stat_rectangle_no_matches"
    const val AUTOCOMPLETE_SAVE_ACTION = "gear_stat_rectangle_autocomplete_save_action"
}

/** The special "Other" radio option id on the radio-list sub-screen (screen 2) — not a real rectangle code. */
private const val OTHER_OPTION_ID = "other"
private const val OTHER_BUTTON_WIDTH_FRACTION = 0.5f

/**
 * Per-confirmed-gear "Where was the majority of your catch caught using {gear}?" statistical
 * sub-rectangle selection (Phase 4). Looped once for each
 * [GearUse] with `confirmedUsedOnTrip == true` still missing a `statisticalSubRectangleCode` — see
 * [nextGearUsePendingStatRectangle]/[nextWizardStepForDraft]. Re-dispatches to itself (`onNavigate` called
 * with the same [WizardStep.GearStatRectangle]) while another confirmed gear remains, exactly like every
 * other screen's generic [CatchRecordFlowEvent.SaveAndContinue] dispatch — no bespoke "next gear" event.
 *
 * Three sub-screens (Grid/RadioList/Autocomplete) are modeled as one step with local, non-ViewModel state
 * — mirroring [DeparturePortScreen]'s `DeparturePortEntryMode` pattern — rather than three separate
 * [WizardStep]s, since which sub-screen is showing is pure UI navigation, not draft state.
 */
@Suppress("FunctionNaming")
@Composable
fun MapScreen(
    viewModel: CatchRecordFlowViewModel,
    onNavigate: (WizardStep) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    editGearUseId: String? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val mapDataState = rememberMapDataUiState()
    MapScreen(
        state = state,
        editGearUseId = editGearUseId,
        onSubmit = { updatedDraft ->
            if (editGearUseId != null) {
                val newCode =
                    updatedDraft.gearUses.firstOrNull { it.id == editGearUseId }?.statisticalSubRectangleCode
                if (newCode != null) {
                    viewModel.dispatch(CatchRecordFlowEvent.EditGearStatRectangle(editGearUseId, newCode))
                }
                onNavigate(WizardStep.CheckYourAnswers)
            } else {
                val nextStep = nextWizardStepForDraft(updatedDraft)
                viewModel.dispatch(CatchRecordFlowEvent.SaveAndContinue(updatedDraft, nextStep))
                onNavigate(nextStep)
            }
        },
        onRetry = { viewModel.dispatch(CatchRecordFlowEvent.Retry) },
        onBack = onBack,
        modifier = modifier,
        mapDataState = mapDataState,
    )
}

@Suppress("FunctionNaming")
@Composable
internal fun MapScreen(
    state: CatchRecordFlowViewState,
    onSubmit: (CatchRecordDraft) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    editGearUseId: String? = null,
    onRetry: () -> Unit = {},
    mapDataState: MapDataUiState = MapDataUiState(UiStatus.Loading, {}),
) {
    val draft = (state.status as? UiStatus.Content<CatchRecordDraft>)?.value
    val currentGearUse = MapSupport.currentGearUseFor(draft, editGearUseId)
    val gearType = currentGearUse?.let { gearUse -> state.gearTypes.firstOrNull { it.id == gearUse.gearTypeId } }
    val gearNameWithMeasurement =
        currentGearUse?.let { MapSupport.gearNameWithIdentifyingMeasurementFor(gearType, it) }
    var entryMode by
        rememberSaveable(currentGearUse?.id) { mutableStateOf(MapEntryMode.Grid) }

    CatchRecordWizardScaffold(
        screenTestTag = MapScreenTestTags.SCREEN,
        title =
            when {
                gearNameWithMeasurement == null -> stringResource(R.string.gear_stat_rectangle_title_fallback)
                entryMode == MapEntryMode.Grid ->
                    stringResource(R.string.gear_stat_rectangle_grid_title, gearNameWithMeasurement)

                else -> stringResource(R.string.gear_stat_rectangle_other_title, gearNameWithMeasurement)
            },
        onBack = onBack,
        modifier = modifier,
        referenceNumber = state.catchRecordReference,
    ) {
        MapScreenStatusContent(
            draft = draft,
            currentGearUse = currentGearUse,
            state = state,
            entryModeState = MapEntryModeState(entryMode) { entryMode = it },
            onSubmit = onSubmit,
            onRetry = onRetry,
            mapDataState = mapDataState,
        )
    }
}

@Suppress("FunctionNaming")
@Composable
fun MapScreenContent(
    gearUse: GearUse,
    departurePort: Port?,
    rectangleOptions: StatRectangleOptions,
    entryModeState: MapEntryModeState,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
    mapDataState: MapDataUiState = MapDataUiState(UiStatus.Loading, {}),
) {
    when (entryModeState.entryMode) {
        MapEntryMode.Grid ->
            MapGridContent(
                gearUse = gearUse,
                departurePort = departurePort,
                nearbyRectangles = rectangleOptions.nearby,
                mapDataState = mapDataState,
                onOtherSelected = { entryModeState.onEntryModeChange(MapEntryMode.RadioList) },
                onSubmit = onSubmit,
                modifier = modifier,
            )

        MapEntryMode.RadioList ->
            MapRadioListContent(
                gearUse = gearUse,
                nearbyRectangles = rectangleOptions.nearby,
                onOtherSelected = { entryModeState.onEntryModeChange(MapEntryMode.Autocomplete) },
                onSubmit = onSubmit,
                modifier = modifier,
            )

        MapEntryMode.Autocomplete ->
            MapAutocompleteContent(
                gearUse = gearUse,
                allRectangles = rectangleOptions.all,
                onSubmit = onSubmit,
                modifier = modifier,
            )
    }
}

/**
 * Screen 1: the real offline fisheries map (see [MapCanvas]) — replaces the previous schematic
 * [uk.gov.defra.mmocatchrecord.common.design.GdsStatisticalRectangleGrid]. The confirmed screenshots show no explicit "Save and continue" button here
 * (just "tap to select" + an "Other" link) — a Save action is added anyway as a deliberate, flagged
 * deviation: auto-navigating on tap is a poor pattern for screen-reader/switch-access users, and every
 * other selection screen in this wizard requires an explicit confirmation action.
 */
@Suppress("FunctionNaming")
@Composable
private fun MapGridContent(
    gearUse: GearUse,
    departurePort: Port?,
    nearbyRectangles: List<StatisticalSubRectangle>,
    mapDataState: MapDataUiState,
    onOtherSelected: () -> Unit,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedCode by
        rememberSaveable(gearUse.id) { mutableStateOf(gearUse.statisticalSubRectangleCode) }
    var showError by rememberSaveable(gearUse.id) { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text(
            text = stringResource(R.string.gear_stat_rectangle_body_nearby),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = stringResource(R.string.gear_stat_rectangle_body_select_grid),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = stringResource(R.string.gear_stat_rectangle_body_other_hint),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (showError) {
            Text(
                text = stringResource(R.string.gear_stat_rectangle_error_required),
                color = MmoColors.ErrorRed,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(MapScreenTestTags.ERROR_MESSAGE),
            )
        }
        when (mapDataState.mapStatus) {
            UiStatus.Idle, UiStatus.Loading -> WizardLoadingState()
            is UiStatus.Error ->
                // Never crash: the map data failed to load. The "Other" button below (rendered
                // unconditionally, outside this `when`) still lets the user continue via the fully
                // keyboard/TalkBack-accessible radio-list/autocomplete path.
                WizardErrorState(
                    message = stringResource(R.string.gear_stat_rectangle_map_unavailable),
                    testTag = MapScreenTestTags.MAP_ERROR,
                    isRetryable = mapDataState.mapStatus.isRetryable,
                    onRetry = mapDataState.onRetryMapData,
                )
            is UiStatus.Content -> {
                val dataset = mapDataState.mapStatus.value
                val initialCamera =
                    remember(gearUse.id, dataset) {
                        MapCameraSupport.initialCameraFor(departurePort, dataset, nearbyRectangles)
                    }
                var camera by
                    rememberSaveable(gearUse.id, stateSaver = MapCamera.Saver) { mutableStateOf(initialCamera) }

                Box(modifier = Modifier.fillMaxWidth()) {
                    MapCanvas(
                        dataset = dataset,
                        cameraState = MapCameraState(camera) { camera = it },
                        selection =
                            MapSelectionState(selectedCode) {
                                selectedCode = it
                                showError = false
                            },
                        modifier = Modifier.fillMaxWidth(),
                        onChooseFromListRequested = onOtherSelected,
                    )
                    SecondaryActionButton(
                        text = stringResource(R.string.gear_stat_rectangle_other_option),
                        onClick = onOtherSelected,
                        modifier =
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(Spacing.s)
                                .fillMaxWidth(OTHER_BUTTON_WIDTH_FRACTION)
                                .background(MmoColors.White)
                                .testTag(MapScreenTestTags.GRID_OTHER_ACTION),
                    )
                }
                Text(
                    text =
                        selectedCode?.let { stringResource(R.string.gear_stat_rectangle_selected_area, it) }
                            ?: stringResource(R.string.gear_stat_rectangle_selected_area_none),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.testTag(MapScreenTestTags.MAP_SELECTED_TEXT),
                )
            }
        }
        if (mapDataState.mapStatus !is UiStatus.Content) {
            // Fallback path (map data still loading, or failed to load): "Other" must remain reachable —
            // see [MapCanvas]/docs/development/offline-map.md "never crash".
            SecondaryActionButton(
                text = stringResource(R.string.gear_stat_rectangle_other_option),
                onClick = onOtherSelected,
                modifier =
                    Modifier
                        .fillMaxWidth(OTHER_BUTTON_WIDTH_FRACTION)
                        .testTag(MapScreenTestTags.GRID_OTHER_ACTION),
            )
        }
        PrimaryActionButton(
            text = stringResource(R.string.save_and_continue),
            onClick = {
                val code = selectedCode
                if (code == null) {
                    showError = true
                } else {
                    onSubmit(code)
                }
            },
            modifier = Modifier.testTag(MapScreenTestTags.GRID_SAVE_ACTION),
        )
    }
}

/** Screen 2: the same nearby codes as a vertical radio list, plus a final "Other" option. */
@Suppress("FunctionNaming")
@Composable
private fun MapRadioListContent(
    gearUse: GearUse,
    nearbyRectangles: List<StatisticalSubRectangle>,
    onOtherSelected: () -> Unit,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedOptionId by
        rememberSaveable(gearUse.id) { mutableStateOf(gearUse.statisticalSubRectangleCode) }
    var showError by rememberSaveable(gearUse.id) { mutableStateOf(false) }
    val options =
        remember(nearbyRectangles) {
            nearbyRectangles.map { GdsRadioOption(it.code, it.code) } +
                GdsRadioOption(OTHER_OPTION_ID, "")
        }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text(
            text = stringResource(R.string.gear_stat_rectangle_body_nearby),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = stringResource(R.string.gear_stat_rectangle_body_select_other),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (showError) {
            Text(
                text = stringResource(R.string.gear_stat_rectangle_error_required),
                color = MmoColors.ErrorRed,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(MapScreenTestTags.ERROR_MESSAGE),
            )
        }
        val otherLabel = stringResource(R.string.gear_stat_rectangle_other_option)
        GdsRadioGroup(
            options = options.map { if (it.id == OTHER_OPTION_ID) it.copy(label = otherLabel) else it },
            selectedOptionId = selectedOptionId,
            onOptionSelected = {
                selectedOptionId = it
                showError = false
            },
            optionTestTagPrefix = MapScreenTestTags.RADIO_OPTION_PREFIX,
        )
        PrimaryActionButton(
            text = stringResource(R.string.save_and_continue),
            onClick = {
                when (val id = selectedOptionId) {
                    null -> showError = true
                    OTHER_OPTION_ID -> onOtherSelected()
                    else -> onSubmit(id)
                }
            },
            modifier = Modifier.testTag(MapScreenTestTags.RADIO_SAVE_ACTION),
        )
    }
}

/**
 * Screen 3: free-text search over the full/global rectangle code list. Unlike the grid/radio-list, this
 * validates the raw typed text against [MapRectangleFormatValidator] (required + format) via
 * [WizardErrorSummary] — matching
 * [uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear.GearMeasurementScreenContent]'s pattern for free-text/numeric input,
 * since a typed code need not appear in the (locally stubbed, non-exhaustive) suggestion list to be valid.
 */
@Suppress("FunctionNaming")
@Composable
private fun MapAutocompleteContent(
    gearUse: GearUse,
    allRectangles: List<StatisticalSubRectangle>,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchQuery by rememberSaveable(gearUse.id) { mutableStateOf(gearUse.statisticalSubRectangleCode.orEmpty()) }
    var error by remember(gearUse.id) { mutableStateOf<MapRectangleInputError?>(null) }
    val summaryFocusRequester = remember { FocusRequester() }
    val fieldFocusRequester = remember { FocusRequester() }
    val suggestions =
        remember(
            searchQuery,
            allRectangles,
        ) { MapRectangleSearch.filterSuggestions(searchQuery, allRectangles) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        error?.let { currentError ->
            WizardErrorSummary(
                title = stringResource(R.string.error_summary_title),
                items =
                    listOf(
                        WizardErrorSummaryItem(
                            message =
                                stringResource(
                                    if (currentError == MapRectangleInputError.Format) {
                                        R.string.gear_stat_rectangle_error_format
                                    } else {
                                        R.string.gear_stat_rectangle_error_required
                                    },
                                ),
                            onClick = { fieldFocusRequester.requestFocus() },
                        ),
                    ),
                focusRequester = summaryFocusRequester,
                testTag = MapScreenTestTags.ERROR_SUMMARY,
            )
        }
        GdsAutocompleteField(
            label = stringResource(R.string.gear_stat_rectangle_autocomplete_label),
            value = searchQuery,
            options = suggestions.map { GdsAutocompleteOption(it.id, it.code) },
            onValueChange = {
                searchQuery = it
                error = null
            },
            onOptionSelected = {
                searchQuery = it.label
                error = null
            },
            fieldTestTag = MapScreenTestTags.AUTOCOMPLETE_FIELD,
            liveRegionTestTag = MapScreenTestTags.LIVE_REGION,
            suggestionTestTagPrefix = MapScreenTestTags.SUGGESTION_PREFIX,
            noMatchesTestTag = MapScreenTestTags.NO_MATCHES,
            noMatchesText = stringResource(R.string.gear_stat_rectangle_no_matches_found),
            modifier = Modifier.focusRequester(fieldFocusRequester),
        )
        PrimaryActionButton(
            text = stringResource(R.string.save_and_continue),
            onClick = {
                val result = MapRectangleFormatValidator.validate(searchQuery)
                val code = result.code
                if (code != null) {
                    error = null
                    onSubmit(code)
                } else {
                    error = result.error
                }
            },
            modifier = Modifier.testTag(MapScreenTestTags.AUTOCOMPLETE_SAVE_ACTION),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Suppress("FunctionNaming")
@Composable
fun MapScreenGridPreview() {
    val gearType =
        GearType(
            id = "gear-seine-nets",
            name = "Seine nets (not specified)",
        )
    val gearUse =
        GearUse(
            id = "gear-use-1",
            gearTypeId = gearType.id,
            statisticalSubRectangleCode = null,
            measurements = mapOf(GearMeasurementFieldKeys.MESH_SIZE_MM to MeasurementValue.Numeric(100.0, "mm")),
            confirmedUsedOnTrip = true,
        )
    val samplePort = Port("port-hastings", "Hastings", PREVIEW_AREA_HASTINGS)
    val sampleRectangles =
        listOf(
            StatisticalSubRectangle("rect-1", "38E95", PREVIEW_AREA_HASTINGS),
            StatisticalSubRectangle("rect-2", "38E98", PREVIEW_AREA_HASTINGS),
            StatisticalSubRectangle("rect-3", "38F02", PREVIEW_AREA_HASTINGS),
        )
    val sampleDraft =
        CatchRecordDraft(
            id = "draft-1",
            vesselId = "vessel-1",
            departurePort = PortSelection(samplePort.id, PortSelectionMode.FirstTime),
            gearUses = listOf(gearUse),
            modifiedAtEpochMillis = 1605830400000L,
            status = DraftStatus.Draft,
        )
    val state =
        CatchRecordFlowViewState(
            status = UiStatus.Content(sampleDraft),
            gearTypes = listOf(gearType),
            ports = listOf(samplePort),
            statisticalSubRectangles = sampleRectangles,
        )
    MmoTheme {
        AppLanguageProvider(language = "en") {
            MapScreen(state = state, onSubmit = {}, onBack = {})
        }
    }
}

/** Shared sample gear use/rectangles for the RadioList/Autocomplete previews below (mirrors [MapScreenGridPreview]). */
private data class MapPreviewFixture(
    val gearUse: GearUse,
    val nearbyRectangles: List<StatisticalSubRectangle>,
    val allRectangles: List<StatisticalSubRectangle>,
)

private const val PREVIEW_AREA_HASTINGS = "AREA-HASTINGS"

@Suppress("MagicNumber")
private fun mapPreviewFixture(): MapPreviewFixture {
    val gearUse =
        GearUse(
            id = "gear-use-1",
            gearTypeId = "gear-seine-nets",
            statisticalSubRectangleCode = null,
            measurements = mapOf(GearMeasurementFieldKeys.MESH_SIZE_MM to MeasurementValue.Numeric(100.0, "mm")),
            confirmedUsedOnTrip = true,
        )
    val sampleRectangles =
        listOf(
            StatisticalSubRectangle("rect-1", "38E95", PREVIEW_AREA_HASTINGS),
            StatisticalSubRectangle("rect-2", "38E98", PREVIEW_AREA_HASTINGS),
            StatisticalSubRectangle("rect-3", "38F02", PREVIEW_AREA_HASTINGS),
        )
    return MapPreviewFixture(
        gearUse = gearUse,
        nearbyRectangles = sampleRectangles,
        allRectangles = sampleRectangles,
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Suppress("FunctionNaming")
@Composable
fun MapScreenRadioListPreview() {
    val fixture = mapPreviewFixture()
    MmoTheme {
        AppLanguageProvider(language = "en") {
            MapScreenContent(
                gearUse = fixture.gearUse,
                departurePort = null,
                rectangleOptions = StatRectangleOptions(fixture.nearbyRectangles, fixture.allRectangles),
                entryModeState = MapEntryModeState(MapEntryMode.RadioList) {},
                onSubmit = {},
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Suppress("FunctionNaming")
@Composable
fun MapScreenAutocompletePreview() {
    val fixture = mapPreviewFixture()
    MmoTheme {
        AppLanguageProvider(language = "en") {
            MapScreenContent(
                gearUse = fixture.gearUse,
                departurePort = null,
                rectangleOptions = StatRectangleOptions(fixture.nearbyRectangles, fixture.allRectangles),
                entryModeState = MapEntryModeState(MapEntryMode.Autocomplete) {},
                onSubmit = {},
            )
        }
    }
}
