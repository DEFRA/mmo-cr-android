package uk.gov.defra.mmocatchrecord.feature.home.presentation

import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.core.architecture.ViewState
import uk.gov.defra.mmocatchrecord.feature.home.domain.HomeSummary

/** Immutable UI state for [HomeViewModel]. */
data class HomeViewState(
    val status: UiStatus<HomeSummary> = UiStatus.Idle,
) : ViewState

/** UI-originated events for the Home screen; none needed yet — summary collection is reactive (ADR 0014 Phase B). */
sealed interface HomeEvent
