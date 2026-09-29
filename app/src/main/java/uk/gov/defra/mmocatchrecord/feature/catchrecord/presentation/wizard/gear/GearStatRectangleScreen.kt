@file:Suppress("detekt.FunctionNaming", "detekt.LongMethod", "detekt.LongParameterList", "detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear

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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.common.design.AppLanguageProvider
import uk.gov.defra.mmocatchrecord.common.design.GdsAutocompleteField
import uk.gov.defra.mmocatchrecord.common.design.GdsAutocompleteOption
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
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.GeoBoundingBox
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.GeoPoint
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.MapGeometryDataset
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.MapPort
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.StatisticalSubRectangleGeometry
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
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.nextGearUsePendingStatRectangle
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.nextWizardStepForDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear.map.AccessibleStatisticalAreaMap
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear.map.MapCentring
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.trip.DeparturePortScreen

/** Which sub-screen of the [WizardStep.GearStatRectangle] step is currently shown — see [GearStatRectangleScreenContent]. */
enum class GearStatRectangleEntryMode {
    NearbyMap,
    Autocomplete,
}

object GearStatRectangleScreenTestTags {
    const val SCREEN = "gear_stat_rectangle_screen"
    const val ERROR_SUMMARY = "gear_stat_rectangle_error_summary"
    const val ERROR_MESSAGE = "gear_stat_rectangle_error_message"
    const val NEARBY_MAP_CANVAS = "gear_stat_rectangle_nearby_map_canvas"
    const val NEARBY_MAP_CELL_PREFIX = "gear_stat_rectangle_nearby_map_cell"
    const val NEARBY_OTHER_ACTION = "gear_stat_rectangle_nearby_other_action"
    const val NEARBY_SAVE_ACTION = "gear_stat_rectangle_nearby_save_action"
    const val AUTOCOMPLETE_FIELD = "gear_stat_rectangle_autocomplete_field"
    const val LIVE_REGION = "gear_stat_rectangle_live_region"
    const val SUGGESTION_PREFIX = "gear_stat_rectangle_suggestion"
    const val NO_MATCHES = "gear_stat_rectangle_no_matches"
    const val AUTOCOMPLETE_SAVE_ACTION = "gear_stat_rectangle_autocomplete_save_action"
}

/**
 * Per-confirmed-gear "Where was the majority of your catch caught using {gear}?" statistical
 * sub-rectangle selection (Phase 4). Looped once for each
 * [GearUse] with `confirmedUsedOnTrip == true` still missing a `statisticalSubRectangleCode` — see
 * [nextGearUsePendingStatRectangle]/[nextWizardStepForDraft]. Re-dispatches to itself (`onNavigate` called
 * with the same [WizardStep.GearStatRectangle]) while another confirmed gear remains, exactly like every
 * other screen's generic [CatchRecordFlowEvent.SaveAndContinue] dispatch — no bespoke "next gear" event.
 *
 * Two sub-screens (NearbyMap/Autocomplete) are modelled as one step with local, non-ViewModel state —
 * mirroring [DeparturePortScreen]'s `DeparturePortEntryMode` pattern — rather than two separate
 * [WizardStep]s, since which sub-screen is showing is pure UI navigation, not draft state. "Other" on the
 * nearby map jumps straight to the manual-entry Autocomplete screen — there is no intermediate visible
 * radio-list screen (removed per the confirmed design; see [AccessibleStatisticalAreaMap]'s doc comment for
 * why the nearby map alone already meets WCAG 2.2 AA without one).
 */
@Suppress("FunctionNaming")
@Composable
fun GearStatRectangleScreen(
    viewModel: CatchRecordFlowViewModel,
    onNavigate: (WizardStep) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    editGearUseId: String? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Per ADR 0007, this async load is now owned by the shared ViewModel (see
    // CatchRecordFlowViewState.mapGeometryStatus's doc comment) rather than this screen reaching into Hilt
    // directly via a dedicated EntryPoint + produceState — this LaunchedEffect mirrors the exact same
    // `LaunchedEffect(Unit) { viewModel.dispatch(...) }` idiom CatchRecordFlowScreen uses for EnterFlow.
    LaunchedEffect(Unit) { viewModel.dispatch(CatchRecordFlowEvent.LoadMapGeometry) }
    GearStatRectangleScreen(
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
        onRetryGeometry = { viewModel.dispatch(CatchRecordFlowEvent.LoadMapGeometry) },
        onBack = onBack,
        modifier = modifier,
    )
}

@Suppress("FunctionNaming")
@Composable
internal fun GearStatRectangleScreen(
    state: CatchRecordFlowViewState,
    onSubmit: (CatchRecordDraft) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    editGearUseId: String? = null,
    onRetry: () -> Unit = {},
    onRetryGeometry: () -> Unit = {},
) {
    val draft = (state.status as? UiStatus.Content<CatchRecordDraft>)?.value
    val currentGearUse =
        if (editGearUseId != null) {
            draft?.gearUses?.firstOrNull { it.id == editGearUseId }
        } else {
            draft?.let(::nextGearUsePendingStatRectangle)
        }
    val gearType = currentGearUse?.let { gearUse -> state.gearTypes.firstOrNull { it.id == gearUse.gearTypeId } }
    val gearNameWithMeasurement =
        currentGearUse?.let { GearStatRectangleSupport.gearNameWithIdentifyingMeasurementFor(gearType, it) }
    var entryMode by
        rememberSaveable(currentGearUse?.id) { mutableStateOf(GearStatRectangleEntryMode.NearbyMap) }

    CatchRecordWizardScaffold(
        screenTestTag = GearStatRectangleScreenTestTags.SCREEN,
        title =
            when {
                gearNameWithMeasurement == null -> stringResource(R.string.gear_stat_rectangle_title_fallback)
                entryMode == GearStatRectangleEntryMode.NearbyMap ->
                    stringResource(R.string.gear_stat_rectangle_nearby_title, gearNameWithMeasurement)

                else -> stringResource(R.string.gear_stat_rectangle_other_title, gearNameWithMeasurement)
            },
        onBack = onBack,
        modifier = modifier,
        referenceNumber = state.catchRecordReference,
    ) {
        when (val status = state.status) {
            UiStatus.Idle, UiStatus.Loading -> WizardLoadingState()
            is UiStatus.Error ->
                WizardErrorState(
                    message = status.message,
                    testTag = GearStatRectangleScreenTestTags.ERROR_MESSAGE,
                    isRetryable = status.isRetryable,
                    onRetry = onRetry,
                )
            is UiStatus.Content ->
                if (draft == null || currentGearUse == null) {
                    // Defensive only: normal navigation only reaches this screen while
                    // nextGearUsePendingStatRectangle(draft) is non-null (add path), or editGearUseId
                    // resolves to a real gear use (check-your-answers edit path) — see
                    // nextWizardStepForDraft / editRouteFor.
                    WizardErrorState(
                        stringResource(R.string.gear_stat_rectangle_missing_gear),
                        GearStatRectangleScreenTestTags.ERROR_MESSAGE,
                    )
                } else {
                    val departurePort = state.ports.firstOrNull { it.id == draft.departurePort?.portId }
                    GearStatRectangleScreenContent(
                        gearUse = currentGearUse,
                        nearbyRectangles =
                            GearStatRectangleSupport.nearbyRectanglesFor(departurePort, state.statisticalSubRectangles),
                        allRectangles = state.statisticalSubRectangles,
                        entryMode = entryMode,
                        onEntryModeChange = { entryMode = it },
                        mapGeometryStatus = state.mapGeometryStatus,
                        onRetryGeometry = onRetryGeometry,
                        departurePortName = departurePort?.name,
                        onSubmit = { code ->
                            val updatedGearUse = currentGearUse.copy(statisticalSubRectangleCode = code)
                            val updatedDraft =
                                draft.copy(
                                    gearUses =
                                        draft.gearUses.map {
                                            if (it.id == updatedGearUse.id) updatedGearUse else it
                                        },
                                )
                            onSubmit(updatedDraft)
                        },
                    )
                }
        }
    }
}

@Suppress("FunctionNaming")
@Composable
fun GearStatRectangleScreenContent(
    gearUse: GearUse,
    nearbyRectangles: List<StatisticalSubRectangle>,
    allRectangles: List<StatisticalSubRectangle>,
    entryMode: GearStatRectangleEntryMode,
    onEntryModeChange: (GearStatRectangleEntryMode) -> Unit,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
    mapGeometryStatus: UiStatus<MapGeometryDataset> = UiStatus.Idle,
    onRetryGeometry: () -> Unit = {},
    departurePortName: String? = null,
) {
    when (entryMode) {
        GearStatRectangleEntryMode.NearbyMap ->
            GearStatRectangleNearbyMapContent(
                gearUse = gearUse,
                nearbyRectangles = nearbyRectangles,
                geometryStatus = mapGeometryStatus,
                departurePortName = departurePortName,
                onOtherSelected = { onEntryModeChange(GearStatRectangleEntryMode.Autocomplete) },
                onSubmit = onSubmit,
                modifier = modifier,
                onRetryGeometry = onRetryGeometry,
            )

        GearStatRectangleEntryMode.Autocomplete ->
            GearStatRectangleAutocompleteContent(
                gearUse = gearUse,
                allRectangles = allRectangles,
                onSubmit = onSubmit,
                modifier = modifier,
            )
    }
}

/**
 * Screen 1 (default, and only, map screen): the real interactive map ([AccessibleStatisticalAreaMap]),
 * scoped to the small, bounded set of sub-rectangles nearest the departure port ([nearbyRectangles] joined
 * onto the loaded [geometryStatus] via [GearStatRectangleSupport.nearbyGeometryFor]). The map is the
 * **sole** selection UI here — there is no separate visible radio list — see
 * [AccessibleStatisticalAreaMap]'s doc comment for why this is safe (a small, bounded cell count) and how
 * it still meets WCAG 2.2 AA (per-cell accessible overlay + live-region announcement, in place of a visible
 * list). "Other" ([onOtherSelected]) jumps straight to the free-text [GearStatRectangleAutocompleteContent]
 * screen — there is no intermediate visible radio-list screen.
 */
@Suppress("FunctionNaming", "LongMethod")
@Composable
private fun GearStatRectangleNearbyMapContent(
    gearUse: GearUse,
    nearbyRectangles: List<StatisticalSubRectangle>,
    geometryStatus: UiStatus<MapGeometryDataset>,
    onOtherSelected: () -> Unit,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
    onRetryGeometry: () -> Unit = {},
    departurePortName: String? = null,
) {
    var selectedCode by
        rememberSaveable(gearUse.id) { mutableStateOf(gearUse.statisticalSubRectangleCode) }
    var showError by rememberSaveable(gearUse.id) { mutableStateOf(false) }
    var focusSummary by remember { mutableStateOf(false) }
    val summaryFocusRequester = remember { FocusRequester() }

    // Accessible focus/announcement on a failed validation — matches every other sub-screen's
    // summaryFocusRequester + LaunchedEffect(focusSummary) convention (see GearStatRectangleAutocompleteContent).
    LaunchedEffect(focusSummary) {
        if (focusSummary) {
            summaryFocusRequester.requestFocus()
            focusSummary = false
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text(
            text = stringResource(R.string.gear_stat_rectangle_body_nearby),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = stringResource(R.string.gear_stat_rectangle_body_select),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = stringResource(R.string.gear_stat_rectangle_body_other_hint),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (showError) {
            WizardErrorSummary(
                title = stringResource(R.string.error_summary_title),
                items =
                    listOf(
                        WizardErrorSummaryItem(
                            message = stringResource(R.string.gear_stat_rectangle_map_error_required),
                            onClick = {},
                        ),
                    ),
                focusRequester = summaryFocusRequester,
                testTag = GearStatRectangleScreenTestTags.ERROR_SUMMARY,
            )
        }

        // Live-region announcement of selection changes — independent of the map's own single summarised
        // semantics node.
        Text(
            text =
                selectedCode
                    ?.let { stringResource(R.string.gear_stat_rectangle_map_selected_announcement, it) }
                    .orEmpty(),
            modifier = Modifier.testTag(GearStatRectangleScreenTestTags.LIVE_REGION),
            style = MaterialTheme.typography.labelSmall,
        )

        when (geometryStatus) {
            UiStatus.Idle, UiStatus.Loading -> WizardLoadingState()
            is UiStatus.Error ->
                WizardErrorState(
                    message = geometryStatus.message,
                    testTag = GearStatRectangleScreenTestTags.ERROR_MESSAGE,
                    isRetryable = geometryStatus.isRetryable,
                    onRetry = onRetryGeometry,
                )
            is UiStatus.Content -> {
                val geometry = geometryStatus.value
                val nearbyGeometry =
                    remember(geometry, nearbyRectangles) {
                        GearStatRectangleSupport.nearbyGeometryFor(nearbyRectangles.map { it.code }, geometry)
                    }
                val initialCentre =
                    remember(geometry, departurePortName) {
                        MapCentring.initialCentreFor(departurePortName, geometry.ports)
                    }

                AccessibleStatisticalAreaMap(
                    nearbyGeometry = nearbyGeometry,
                    landPolygons = geometry.landPolygons,
                    ports = geometry.ports,
                    selectedCode = selectedCode,
                    onCodeSelected = { code ->
                        selectedCode = code
                        showError = false
                    },
                    initialCentre = initialCentre,
                    contentDescription = stringResource(R.string.gear_stat_rectangle_nearby_map_content_description),
                    cameraResetKey = gearUse.id,
                    cellTestTagPrefix = GearStatRectangleScreenTestTags.NEARBY_MAP_CELL_PREFIX,
                    canvasTestTag = GearStatRectangleScreenTestTags.NEARBY_MAP_CANVAS,
                )
            }
        }

        SecondaryActionButton(
            text = stringResource(R.string.gear_stat_rectangle_other_option),
            onClick = onOtherSelected,
            modifier = Modifier.testTag(GearStatRectangleScreenTestTags.NEARBY_OTHER_ACTION),
        )
        PrimaryActionButton(
            text = stringResource(R.string.save_and_continue),
            onClick = {
                val code = selectedCode
                if (code == null) {
                    showError = true
                    focusSummary = true
                } else {
                    onSubmit(code)
                }
            },
            modifier = Modifier.testTag(GearStatRectangleScreenTestTags.NEARBY_SAVE_ACTION),
        )
    }
}

/**
 * Screen 2: free-text search over the full/global rectangle code list. Unlike the nearby map, this
 * validates the raw typed text against [StatisticalSubRectangleFormatValidator] (required + format) via
 * [WizardErrorSummary] — matching [GearMeasurementScreenContent]'s pattern for free-text/numeric input,
 * since a typed code need not appear in the (locally stubbed, non-exhaustive) suggestion list to be valid.
 */
@Suppress("FunctionNaming")
@Composable
private fun GearStatRectangleAutocompleteContent(
    gearUse: GearUse,
    allRectangles: List<StatisticalSubRectangle>,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchQuery by rememberSaveable(gearUse.id) { mutableStateOf(gearUse.statisticalSubRectangleCode.orEmpty()) }
    var error by remember(gearUse.id) { mutableStateOf<StatisticalSubRectangleInputError?>(null) }
    val summaryFocusRequester = remember { FocusRequester() }
    val fieldFocusRequester = remember { FocusRequester() }
    val suggestions =
        remember(
            searchQuery,
            allRectangles,
        ) { StatisticalSubRectangleSearch.filterSuggestions(searchQuery, allRectangles) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        error?.let { currentError ->
            WizardErrorSummary(
                title = stringResource(R.string.error_summary_title),
                items =
                    listOf(
                        WizardErrorSummaryItem(
                            message =
                                stringResource(
                                    if (currentError == StatisticalSubRectangleInputError.Format) {
                                        R.string.gear_stat_rectangle_error_format
                                    } else {
                                        R.string.gear_stat_rectangle_error_required
                                    },
                                ),
                            onClick = { fieldFocusRequester.requestFocus() },
                        ),
                    ),
                focusRequester = summaryFocusRequester,
                testTag = GearStatRectangleScreenTestTags.ERROR_SUMMARY,
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
            fieldTestTag = GearStatRectangleScreenTestTags.AUTOCOMPLETE_FIELD,
            liveRegionTestTag = GearStatRectangleScreenTestTags.LIVE_REGION,
            suggestionTestTagPrefix = GearStatRectangleScreenTestTags.SUGGESTION_PREFIX,
            noMatchesTestTag = GearStatRectangleScreenTestTags.NO_MATCHES,
            noMatchesText = stringResource(R.string.gear_stat_rectangle_no_matches_found),
            modifier = Modifier.focusRequester(fieldFocusRequester),
        )
        PrimaryActionButton(
            text = stringResource(R.string.save_and_continue),
            onClick = {
                val result = StatisticalSubRectangleFormatValidator.validate(searchQuery)
                val code = result.code
                if (code != null) {
                    error = null
                    onSubmit(code)
                } else {
                    error = result.error
                }
            },
            modifier = Modifier.testTag(GearStatRectangleScreenTestTags.AUTOCOMPLETE_SAVE_ACTION),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Suppress("FunctionNaming")
@Composable
fun GearStatRectangleScreen_NearbyMapPreview() {
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
    val samplePort = Port("port-hastings", "Hastings", "AREA-HASTINGS")
    val sampleRectangles =
        listOf(
            StatisticalSubRectangle("rect-1", "38E95", "AREA-HASTINGS"),
            StatisticalSubRectangle("rect-2", "38E98", "AREA-HASTINGS"),
            StatisticalSubRectangle("rect-3", "38F02", "AREA-HASTINGS"),
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

    fun previewRectangle(
        code: String,
        minLat: Double,
        maxLat: Double,
        minLng: Double,
        maxLng: Double,
    ) = StatisticalSubRectangleGeometry(
        subCode = code,
        parentIcesName = "ICES-$code",
        rings =
            listOf(
                listOf(
                    GeoPoint(minLat, minLng),
                    GeoPoint(minLat, maxLng),
                    GeoPoint(maxLat, maxLng),
                    GeoPoint(maxLat, minLng),
                ),
            ),
        bboxCentroid = GeoPoint((minLat + maxLat) / 2, (minLng + maxLng) / 2),
        boundingBox = GeoBoundingBox(minLat, maxLat, minLng, maxLng),
        seaOverlapping = true,
    )
    val sampleGeometry =
        MapGeometryDataset(
            landPolygons = emptyList(),
            subRectangles =
                listOf(
                    previewRectangle("38E95", 50.0, 51.0, 0.0, 1.0),
                    previewRectangle("38E98", 51.0, 52.0, 0.0, 1.0),
                ),
            ports = listOf(MapPort("Hastings", GeoPoint(50.855, 0.573))),
        )
    val state =
        CatchRecordFlowViewState(
            status = UiStatus.Content(sampleDraft),
            gearTypes = listOf(gearType),
            ports = listOf(samplePort),
            statisticalSubRectangles = sampleRectangles,
            mapGeometryStatus = UiStatus.Content(sampleGeometry),
        )
    MmoTheme {
        AppLanguageProvider(language = "en") {
            GearStatRectangleScreen(state = state, onSubmit = {}, onBack = {})
        }
    }
}
