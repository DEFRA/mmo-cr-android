package uk.gov.defra.mmocatchrecord.feature.map.presentation

import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.core.architecture.ViewState
import uk.gov.defra.mmocatchrecord.feature.map.domain.CatchLocation

/** Immutable UI state for [MapViewModel]. */
data class MapViewState(
    val status: UiStatus<List<CatchLocation>> = UiStatus.Idle,
) : ViewState

/** UI-originated events for the Map screen. */
sealed interface MapEvent {
    data object Load : MapEvent
}
