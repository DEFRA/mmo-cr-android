package uk.gov.defra.mmocatchrecord.feature.home.presentation

import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.core.architecture.ViewState
import uk.gov.defra.mmocatchrecord.feature.home.domain.HomeSummary

/**
 * Immutable UI state for [HomeViewModel]. [retryingIds] disables a row's Retry button until its status
 * changes (FR8); [offlineRetryMessage] is a transient, self-dismissing message (see ADR 0014 Phase C).
 */
data class HomeViewState(
    val status: UiStatus<HomeSummary> = UiStatus.Idle,
    val retryingIds: Set<String> = emptySet(),
    val offlineRetryMessage: String? = null,
) : ViewState

/** UI-originated intents for [HomeViewModel] (FR8/AC4, CRAR-152 Phase C). */
sealed interface HomeEvent {
    data class RetrySubmission(
        val draftId: String,
    ) : HomeEvent

    data object OfflineRetryMessageShown : HomeEvent
}
