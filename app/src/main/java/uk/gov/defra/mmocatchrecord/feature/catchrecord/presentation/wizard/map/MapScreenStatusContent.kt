package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
                // Defensive only: the add path pins its gear on entry; the edit path supplies a valid editGearUseId.
                WizardErrorState(
                    stringResource(R.string.gear_stat_rectangle_missing_gear),
                    MapScreenTestTags.ERROR_MESSAGE,
                )
            } else {
                val departurePort = state.ports.firstOrNull { it.id == draft.departurePort?.portId }
                val dataset = (mapDataState.mapStatus as? UiStatus.Content<MapDataset>)?.value
                val rectangleOptions =
                    remember(dataset, departurePort, state.statisticalSubRectangles) {
                        if (dataset != null) {
                            // The comprehensive offline map dataset is loaded: prefer its real, complete
                            // code list over the known-incomplete reference-data stub (see
                            // MapSupport.nearbyRectanglesFromDataset/allRectanglesFromDataset and
                            // docs/development/offline-map.md's "Known data-integration gap").
                            StatRectangleOptions(
                                nearby = MapSupport.nearbyRectanglesFromDataset(departurePort, dataset),
                                all = MapSupport.allRectanglesFromDataset(dataset),
                                validateExistence = true,
                            )
                        } else {
                            StatRectangleOptions(
                                nearby = MapSupport.nearbyRectanglesFor(departurePort, state.statisticalSubRectangles),
                                all = state.statisticalSubRectangles,
                            )
                        }
                    }
                MapScreenContent(
                    gearUse = currentGearUse,
                    departurePort = departurePort,
                    rectangleOptions = rectangleOptions,
                    entryModeState = entryModeState,
                    onSubmit = { code ->
                        val rectangle = MapSupport.rectangleForCode(code, rectangleOptions)
                        onSubmit(
                            MapSupport.withStatRectangleCode(
                                draft,
                                currentGearUse,
                                code,
                                latitude = rectangle?.centroidLatitude,
                                longitude = rectangle?.centroidLongitude,
                            ),
                        )
                    },
                    mapDataState = mapDataState,
                )
            }
    }
}
