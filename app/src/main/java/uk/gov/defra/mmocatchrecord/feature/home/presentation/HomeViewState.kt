package uk.gov.defra.mmocatchrecord.feature.home.presentation

import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.core.architecture.ViewState
import uk.gov.defra.mmocatchrecord.feature.home.domain.HomeSummary

/** Immutable UI state for [HomeViewModel]. */
data class HomeViewState(
    val status: UiStatus<HomeSummary> = UiStatus.Idle,
) : ViewState

/**
 * UI-originated events for the Home screen. Sign-out is intentionally not modelled here: it is a
 * root-level session concern owned by `core.root.SessionCoordinator` (`RootEvent.SignedOut`), invoked
 * directly by the composable via its `onSignOut` callback.
 */
sealed interface HomeEvent {
    data object Load : HomeEvent
}
