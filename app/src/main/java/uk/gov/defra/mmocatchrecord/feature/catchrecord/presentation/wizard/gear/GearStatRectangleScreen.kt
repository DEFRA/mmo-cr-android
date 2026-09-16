@file:Suppress("detekt.FunctionNaming", "detekt.LongMethod", "detekt.LongParameterList", "detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear

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
import uk.gov.defra.mmocatchrecord.common.design.GdsStatisticalRectangleGrid
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
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.nextGearUsePendingStatRectangle
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.nextWizardStepForDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.trip.DeparturePortScreen

/** Which sub-screen of the [WizardStep.GearStatRectangle] step is currently shown — see [GearStatRectangleScreenContent]. */
enum class GearStatRectangleEntryMode {
    Grid,
    RadioList,
    Autocomplete,
}

object GearStatRectangleScreenTestTags {
    const val SCREEN = "gear_stat_rectangle_screen"
    const val ERROR_SUMMARY = "gear_stat_rectangle_error_summary"
    const val ERROR_MESSAGE = "gear_stat_rectangle_error_message"
    const val GRID = "gear_stat_rectangle_grid"
    const val GRID_CELL_PREFIX = "gear_stat_rectangle_grid_cell"
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

/**
 * Per-confirmed-gear "Where was the majority of your catch caught using {gear}?" statistical
 * sub-rectangle selection (Phase 4). Looped once for each
 * [GearUse] with `confirmedUsedOnTrip == true` still missing a `statisticalSubRectangleCode` — see
 * [nextGearUsePendingStatRectangle]/[nextWizardStepForDraft]. Re-dispatches to itself (`onNavigate` called
 * with the same [WizardStep.GearStatRectangle]) while another confirmed gear remains, exactly like every
 * other screen's generic [CatchRecordFlowEvent.SaveAndContinue] dispatch — no bespoke "next gear" event.
 *
 * Three sub-screens (Grid/RadioList/Autocomplete) are modelled as one step with local, non-ViewModel state
 * — mirroring [DeparturePortScreen]'s `DeparturePortEntryMode` pattern — rather than three separate
 * [WizardStep]s, since which sub-screen is showing is pure UI navigation, not draft state.
 */
@Suppress("FunctionNaming")
@Composable
fun GearStatRectangleScreen(
    viewModel: CatchRecordFlowViewModel,
    onNavigate: (WizardStep) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    GearStatRectangleScreen(
        state = state,
        onSubmit = { updatedDraft ->
            val nextStep = nextWizardStepForDraft(updatedDraft)
            viewModel.dispatch(CatchRecordFlowEvent.SaveAndContinue(updatedDraft, nextStep))
            onNavigate(nextStep)
        },
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
) {
    val draft = (state.status as? UiStatus.Content<CatchRecordDraft>)?.value
    val currentGearUse = draft?.let(::nextGearUsePendingStatRectangle)
    val gearType = currentGearUse?.let { gearUse -> state.gearTypes.firstOrNull { it.id == gearUse.gearTypeId } }
    val gearNameWithMeasurement =
        currentGearUse?.let { GearStatRectangleSupport.gearNameWithIdentifyingMeasurementFor(gearType, it) }
    var entryMode by
        rememberSaveable(currentGearUse?.id) { mutableStateOf(GearStatRectangleEntryMode.Grid) }

    CatchRecordWizardScaffold(
        screenTestTag = GearStatRectangleScreenTestTags.SCREEN,
        title =
            when {
                gearNameWithMeasurement == null -> stringResource(R.string.gear_stat_rectangle_title_fallback)
                entryMode == GearStatRectangleEntryMode.Grid ->
                    stringResource(R.string.gear_stat_rectangle_grid_title, gearNameWithMeasurement)

                else -> stringResource(R.string.gear_stat_rectangle_other_title, gearNameWithMeasurement)
            },
        onBack = onBack,
        modifier = modifier,
    ) {
        when (val status = state.status) {
            UiStatus.Idle, UiStatus.Loading -> WizardLoadingState()
            is UiStatus.Error -> WizardErrorState(status.message, GearStatRectangleScreenTestTags.ERROR_MESSAGE)
            is UiStatus.Content ->
                if (draft == null || currentGearUse == null) {
                    // Defensive only: normal navigation only reaches this screen while
                    // nextGearUsePendingStatRectangle(draft) is non-null — see nextWizardStepForDraft.
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
) {
    when (entryMode) {
        GearStatRectangleEntryMode.Grid ->
            GearStatRectangleGridContent(
                gearUse = gearUse,
                nearbyRectangles = nearbyRectangles,
                onOtherSelected = { onEntryModeChange(GearStatRectangleEntryMode.RadioList) },
                onSubmit = onSubmit,
                modifier = modifier,
            )

        GearStatRectangleEntryMode.RadioList ->
            GearStatRectangleRadioListContent(
                gearUse = gearUse,
                nearbyRectangles = nearbyRectangles,
                onOtherSelected = { onEntryModeChange(GearStatRectangleEntryMode.Autocomplete) },
                onSubmit = onSubmit,
                modifier = modifier,
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
 * Screen 1: the schematic grid (see [GdsStatisticalRectangleGrid]). The confirmed screenshots show no
 * explicit "Save and continue" button here (just "tap to select" + an "Other" link) — a Save action is
 * added anyway as a deliberate, flagged deviation: auto-navigating on tap is a poor pattern for
 * screen-reader/switch-access users, and every other selection screen in this wizard requires an explicit
 * confirm action.
 */
@Suppress("FunctionNaming")
@Composable
private fun GearStatRectangleGridContent(
    gearUse: GearUse,
    nearbyRectangles: List<StatisticalSubRectangle>,
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
                modifier = Modifier.testTag(GearStatRectangleScreenTestTags.ERROR_MESSAGE),
            )
        }
        GdsStatisticalRectangleGrid(
            codes = nearbyRectangles.map { it.code },
            selectedCode = selectedCode,
            onCodeSelected = {
                selectedCode = it
                showError = false
            },
            cellTestTagPrefix = GearStatRectangleScreenTestTags.GRID_CELL_PREFIX,
            modifier = Modifier.testTag(GearStatRectangleScreenTestTags.GRID),
        )
        SecondaryActionButton(
            text = stringResource(R.string.gear_stat_rectangle_other_option),
            onClick = onOtherSelected,
            modifier = Modifier.testTag(GearStatRectangleScreenTestTags.GRID_OTHER_ACTION),
        )
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
            modifier = Modifier.testTag(GearStatRectangleScreenTestTags.GRID_SAVE_ACTION),
        )
    }
}

/** Screen 2: the same nearby codes as a vertical radio list, plus a final "Other" option. */
@Suppress("FunctionNaming")
@Composable
private fun GearStatRectangleRadioListContent(
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
                modifier = Modifier.testTag(GearStatRectangleScreenTestTags.ERROR_MESSAGE),
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
            optionTestTagPrefix = GearStatRectangleScreenTestTags.RADIO_OPTION_PREFIX,
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
            modifier = Modifier.testTag(GearStatRectangleScreenTestTags.RADIO_SAVE_ACTION),
        )
    }
}

/**
 * Screen 3: free-text search over the full/global rectangle code list. Unlike the grid/radio-list, this
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
fun GearStatRectangleScreen_GridPreview() {
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
    val state =
        CatchRecordFlowViewState(
            status = UiStatus.Content(sampleDraft),
            gearTypes = listOf(gearType),
            ports = listOf(samplePort),
            statisticalSubRectangles = sampleRectangles,
        )
    MmoTheme {
        AppLanguageProvider(language = "en") {
            GearStatRectangleScreen(state = state, onSubmit = {}, onBack = {})
        }
    }
}
