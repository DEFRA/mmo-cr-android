package uk.gov.defra.mmocatchrecord.feature.home.presentation

import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.core.architecture.ViewState
import uk.gov.defra.mmocatchrecord.feature.home.domain.HomeSummary

/**
 * Immutable UI state for [HomeViewModel]. [retryingIds] disables a row's Retry button until its status
 * changes (FR8); [offlineRetryMessage] and [syncConfirmationMessage] are transient, self-dismissing
 * messages (see ADR 0014 Phase C and Phase E respectively).
 */
data class HomeViewState(
    val status: UiStatus<HomeSummary> = UiStatus.Idle,
    val retryingIds: Set<String> = emptySet(),
    val offlineRetryMessage: String? = null,
    val syncConfirmationMessage: String? = null,
) : ViewState

/** UI-originated intents for [HomeViewModel] (FR8/AC4, CRAR-152 Phase C; FR9, Phase E). */
sealed interface HomeEvent {
    data class RetrySubmission(
        val draftId: String,
    ) : HomeEvent

    data object OfflineRetryMessageShown : HomeEvent

    data object SyncConfirmationMessageShown : HomeEvent
}
