package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowViewState
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardErrorState
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardLoadingState

/**
 * Renders the loading/error/content body for [MapScreen] based on [state]'s status — pulled out of
 * [MapScreen] itself to keep its cognitive complexity low (see `MapSupport.currentGearUseFor`/
 * `withStatRectangleCode` for the other extracted helpers).
 */
@Suppress("FunctionNaming")
@Composable
internal fun MapScreenStatusContent(
    draft: CatchRecordDraft?,
    currentGearUse: GearUse?,
    state: CatchRecordFlowViewState,
    entryModeState: MapEntryModeState,
    onSubmit: (CatchRecordDraft) -> Unit,
    onRetry: () -> Unit,
    mapDataState: MapDataUiState,
) {
    when (val status = state.status) {
        UiStatus.Idle, UiStatus.Loading -> WizardLoadingState()
        is UiStatus.Error ->
            WizardErrorState(
                message = status.message,
                testTag = MapScreenTestTags.ERROR_MESSAGE,
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
                    MapScreenTestTags.ERROR_MESSAGE,
                )
            } else {
                val departurePort = state.ports.firstOrNull { it.id == draft.departurePort?.portId }
                MapScreenContent(
                    gearUse = currentGearUse,
                    departurePort = departurePort,
                    rectangleOptions =
                        StatRectangleOptions(
                            nearby = MapSupport.nearbyRectanglesFor(departurePort, state.statisticalSubRectangles),
                            all = state.statisticalSubRectangles,
                        ),
                    entryModeState = entryModeState,
                    onSubmit = { code -> onSubmit(MapSupport.withStatRectangleCode(draft, currentGearUse, code)) },
                    mapDataState = mapDataState,
                )
            }
    }
}
