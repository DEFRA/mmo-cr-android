package uk.gov.defra.mmocatchrecord.feature.home.presentation

import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.core.architecture.ViewState
import uk.gov.defra.mmocatchrecord.feature.home.domain.HomeSummary

/**
 * Immutable UI state for [HomeViewModel]. [syncConfirmationMessage] is a transient, self-dismissing
 * message confirming an automatic background sync (see ADR 0014 Phase E).
 */
data class HomeViewState(
    val status: UiStatus<HomeSummary> = UiStatus.Idle,
    val syncConfirmationMessage: String? = null,
) : ViewState

/** UI-originated intents for [HomeViewModel] (FR9, CRAR-152 Phase E). */
sealed interface HomeEvent {
    data object SyncConfirmationMessageShown : HomeEvent
}
