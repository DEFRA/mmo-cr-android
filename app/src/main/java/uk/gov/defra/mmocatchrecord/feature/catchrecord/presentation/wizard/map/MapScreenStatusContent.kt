@file:Suppress("detekt.LongParameterList")

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
import uk.gov.defra.mmocatchrecord.mapdata.MapDataset

/**
 * Renders the loading/error/content body for [MapScreen] based on [status] — pulled out of [MapScreen]
 * itself to keep its cognitive complexity low (see `MapSupport.currentGearUseFor`/`withStatRectangleCode`
 * for the other extracted helpers).
 */
@Suppress("FunctionNaming")
@Composable
internal fun MapScreenStatusContent(
    status: UiStatus<CatchRecordDraft>,
    draft: CatchRecordDraft?,
    currentGearUse: GearUse?,
    state: CatchRecordFlowViewState,
    entryMode: MapEntryMode,
    onEntryModeChange: (MapEntryMode) -> Unit,
    onSubmit: (CatchRecordDraft) -> Unit,
    onRetry: () -> Unit,
    mapStatus: UiStatus<MapDataset>,
    onRetryMapData: () -> Unit,
) {
    when (status) {
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
                    nearbyRectangles = MapSupport.nearbyRectanglesFor(departurePort, state.statisticalSubRectangles),
                    allRectangles = state.statisticalSubRectangles,
                    entryMode = entryMode,
                    onEntryModeChange = onEntryModeChange,
                    onSubmit = { code -> onSubmit(MapSupport.withStatRectangleCode(draft, currentGearUse, code)) },
                    mapStatus = mapStatus,
                    onRetryMapData = onRetryMapData,
                )
            }
    }
}
