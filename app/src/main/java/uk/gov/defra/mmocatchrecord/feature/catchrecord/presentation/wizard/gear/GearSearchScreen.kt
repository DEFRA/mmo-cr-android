@file:Suppress("detekt.FunctionNaming", "detekt.LongParameterList", "detekt.MaxLineLength")

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.common.design.AppLanguageProvider
import uk.gov.defra.mmocatchrecord.common.design.GdsAutocompleteField
import uk.gov.defra.mmocatchrecord.common.design.GdsAutocompleteOption
import uk.gov.defra.mmocatchrecord.common.design.MmoColors
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.common.design.PrimaryActionButton
import uk.gov.defra.mmocatchrecord.common.design.Spacing
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowEvent
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowViewModel
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowViewState
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordWizardScaffold
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardErrorState
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardLoadingState
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardStep
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.catchRecordReference

object GearSearchScreenTestTags {
    const val SCREEN = "gear_search_screen"
    const val AUTOCOMPLETE_FIELD = "gear_search_autocomplete_field"
    const val LIVE_REGION = "gear_search_live_region"
    const val SUGGESTION_PREFIX = "gear_search_suggestion"
    const val NO_MATCHES = "gear_search_no_matches"
    const val SAVE_ACTION = "gear_search_save_action"
    const val ERROR_MESSAGE = "gear_search_error_message"
}

/**
 * "What gear did you use?" — reuses the exact same accessible autocomplete pattern as port search. The
 * heading varies by entry context (mirroring the ports first-time-vs-repeat pattern): the first gear added
 * to a trip sees "What gear did you use?", while every subsequent "Add another gear" loop back into this
 * same screen sees "Add gear to your list" (confirmed screenshot) — derived from whether the draft already
 * has any [uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse]s, not from separate
 * ViewModel state, since re-entering this screen is a pure navigation loop (see [GearSummaryScreen]'s
 * "Add another gear" action).
 */
@Suppress("FunctionNaming")
@Composable
fun GearSearchScreen(
    viewModel: CatchRecordFlowViewModel,
    onNavigate: (WizardStep) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    GearSearchScreen(
        state = state,
        onSubmit = { gearTypeId ->
            viewModel.dispatch(CatchRecordFlowEvent.GearTypeSelected(gearTypeId))
            onNavigate(WizardStep.GearMeasurement)
        },
        onRetry = { viewModel.dispatch(CatchRecordFlowEvent.Retry) },
        onBack = onBack,
        modifier = modifier,
    )
}

@Suppress("FunctionNaming")
@Composable
internal fun GearSearchScreen(
    state: CatchRecordFlowViewState,
    onSubmit: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
) {
    val isFirstGear =
        (state.status as? UiStatus.Content<CatchRecordDraft>)?.value?.gearUses?.isEmpty() ?: true
    CatchRecordWizardScaffold(
        screenTestTag = GearSearchScreenTestTags.SCREEN,
        title =
            if (isFirstGear) {
                stringResource(R.string.gear_search_title)
            } else {
                stringResource(R.string.gear_search_title_add_another)
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
                    testTag = GearSearchScreenTestTags.ERROR_MESSAGE,
                    isRetryable = status.isRetryable,
                    onRetry = onRetry,
                )
            is UiStatus.Content -> GearSearchScreenContent(gearTypes = state.gearTypes, onSubmit = onSubmit)
        }
    }
}

@Suppress("FunctionNaming")
@Composable
fun GearSearchScreenContent(
    gearTypes: List<GearType>,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedGearTypeId by rememberSaveable { mutableStateOf<String?>(null) }
    var showError by rememberSaveable { mutableStateOf(false) }
    // Gear types with no confirmed measurement schema (TBC placeholders) are never selectable — see
    // GearTypeSearch.selectableGearTypes. Filtering once here (rather than only inside filterSuggestions)
    // also prevents an exact-name match resolving to a hidden gear type's id via free-text entry.
    val selectableGearTypes = remember(gearTypes) { GearTypeSearch.selectableGearTypes(gearTypes) }
    val suggestions =
        remember(searchQuery, selectableGearTypes) { GearTypeSearch.filterSuggestions(searchQuery, selectableGearTypes) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        GdsAutocompleteField(
            label = stringResource(R.string.gear_search_autocomplete_label),
            value = searchQuery,
            options = suggestions.map { GdsAutocompleteOption(it.id, it.name) },
            onValueChange = {
                searchQuery = it
                selectedGearTypeId =
                    selectableGearTypes.firstOrNull { gearType -> gearType.name.equals(it, ignoreCase = true) }?.id
                showError = false
            },
            onOptionSelected = {
                searchQuery = it.label
                selectedGearTypeId = it.id
                showError = false
            },
            fieldTestTag = GearSearchScreenTestTags.AUTOCOMPLETE_FIELD,
            liveRegionTestTag = GearSearchScreenTestTags.LIVE_REGION,
            suggestionTestTagPrefix = GearSearchScreenTestTags.SUGGESTION_PREFIX,
            noMatchesTestTag = GearSearchScreenTestTags.NO_MATCHES,
            noMatchesText = stringResource(R.string.gear_search_no_matches_found),
        )
        if (showError) {
            Text(
                text = stringResource(R.string.gear_selection_error),
                color = MmoColors.ErrorRed,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(GearSearchScreenTestTags.ERROR_MESSAGE),
            )
        }
        PrimaryActionButton(
            text = stringResource(R.string.save_and_continue),
            onClick = {
                val id = selectedGearTypeId
                if (id == null) {
                    showError = true
                } else {
                    onSubmit(id)
                }
            },
            modifier = Modifier.testTag(GearSearchScreenTestTags.SAVE_ACTION),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Suppress("FunctionNaming")
@Composable
fun GearSearchScreen_Preview() {
    val sampleGearTypes =
        listOf(
            GearType(id = "gear-seine-nets", name = "Seine nets (not specified)"),
            GearType(id = "gear-bottom-otter-trawls-tb", name = "Bottom otter trawls (TB)"),
        )
    val sampleDraft =
        CatchRecordDraft(
            id = "draft-1",
            vesselId = "vessel-1",
            modifiedAtEpochMillis = 1605830400000L,
            status = DraftStatus.Draft,
        )
    val state = CatchRecordFlowViewState(status = UiStatus.Content(sampleDraft), gearTypes = sampleGearTypes)
    MmoTheme {
        AppLanguageProvider(language = "en") {
            GearSearchScreen(state = state, onSubmit = {}, onBack = {})
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Suppress("FunctionNaming")
@Composable
fun GearSearchScreen_AddAnotherGearPreview() {
    val sampleGearTypes =
        listOf(
            GearType(id = "gear-drifting-longlines", name = "Drifting longlines"),
        )
    val sampleDraft =
        CatchRecordDraft(
            id = "draft-1",
            vesselId = "vessel-1",
            gearUses =
                listOf(
                    GearUse(
                        id = "gear-use-1",
                        gearTypeId = "gear-seine-nets",
                        statisticalSubRectangleCode = null,
                    ),
                ),
            modifiedAtEpochMillis = 1605830400000L,
            status = DraftStatus.Draft,
        )
    val state = CatchRecordFlowViewState(status = UiStatus.Content(sampleDraft), gearTypes = sampleGearTypes)
    MmoTheme {
        AppLanguageProvider(language = "en") {
            GearSearchScreen(state = state, onSubmit = {}, onBack = {})
        }
    }
}
