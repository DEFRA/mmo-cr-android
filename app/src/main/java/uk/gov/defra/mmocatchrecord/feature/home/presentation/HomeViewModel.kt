package uk.gov.defra.mmocatchrecord.feature.home.presentation

import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import timber.log.Timber
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.common.design.DebugSettingsSection
import uk.gov.defra.mmocatchrecord.common.design.RecordStatusTag
import uk.gov.defra.mmocatchrecord.core.architecture.BaseViewModel
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.RetryCatchRecordSubmissionResult
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.RetryCatchRecordSubmissionUseCase
import uk.gov.defra.mmocatchrecord.feature.home.domain.HomeSummary
import uk.gov.defra.mmocatchrecord.feature.home.domain.ObserveHomeSummaryUseCase
import javax.inject.Inject

/** ViewModel for the Home feature — collects the reactive summary flow for its lifetime (see ADR 0014). */
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val observeHomeSummaryUseCase: ObserveHomeSummaryUseCase,
        private val retryCatchRecordSubmissionUseCase: RetryCatchRecordSubmissionUseCase,
        val debugSettingsSection: DebugSettingsSection,
        @ApplicationContext private val context: Context,
        defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    ) : BaseViewModel<HomeViewState, HomeEvent>(HomeViewState(), defaultDispatcher) {
        private var lastKnownStatusByDraftId: Map<String, RecordStatusTag> = emptyMap()

        init {
            observeSummary()
        }

        override fun dispatch(event: HomeEvent) {
            when (event) {
                is HomeEvent.RetrySubmission -> retrySubmission(event.draftId)
                HomeEvent.OfflineRetryMessageShown -> updateState { it.copy(offlineRetryMessage = null) }
            }
        }

        private fun retrySubmission(draftId: String) {
            if (draftId in currentState.retryingIds) return
            val result = retryCatchRecordSubmissionUseCase(draftId)
            val offlineMessage =
                if (result == RetryCatchRecordSubmissionResult.EnqueuedWhileOffline) {
                    context.getString(R.string.records_retry_offline_message)
                } else {
                    currentState.offlineRetryMessage
                }
            updateState { it.copy(retryingIds = it.retryingIds + draftId, offlineRetryMessage = offlineMessage) }
        }

        private fun observeSummary() {
            updateState { it.copy(status = UiStatus.Loading) }
            launchInViewModelScope {
                observeHomeSummaryUseCase()
                    .catch { error -> updateState { it.copy(status = loadErrorStatus(error)) } }
                    .collect { summary -> applySummary(summary) }
            }
        }

        /** Clears a retrying id only once its draft's status actually changes (brief: FR8 double-tap guard). */
        private fun applySummary(summary: HomeSummary) {
            val newStatusByDraftId = summary.catchRecords.associate { it.id to it.status }
            val stillRetrying =
                currentState.retryingIds.filterTo(mutableSetOf()) { draftId ->
                    newStatusByDraftId[draftId] == lastKnownStatusByDraftId[draftId]
                }
            lastKnownStatusByDraftId = newStatusByDraftId
            updateState {
                it.copy(
                    status = UiStatus.Content(summary),
                    retryingIds = stillRetrying,
                )
            }
        }

        private fun loadErrorStatus(error: Throwable): UiStatus.Error {
            Timber.w(error, "HomeViewModel summary flow failed")
            return UiStatus.Error(
                message = context.getString(R.string.home_summary_load_error),
                isRetryable = true,
            )
        }
    }
