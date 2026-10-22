package uk.gov.defra.mmocatchrecord.feature.home.presentation

import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import timber.log.Timber
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.common.design.RecordStatusTag
import uk.gov.defra.mmocatchrecord.core.architecture.BaseViewModel
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.home.domain.CatchRecordSummary
import uk.gov.defra.mmocatchrecord.feature.home.domain.HomeSummary
import uk.gov.defra.mmocatchrecord.feature.home.domain.ObserveHomeSummaryUseCase
import javax.inject.Inject

/** ViewModel for the Home feature — collects the reactive summary flow for its lifetime (see ADR 0014). */
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val observeHomeSummaryUseCase: ObserveHomeSummaryUseCase,
        @ApplicationContext private val context: Context,
        defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    ) : BaseViewModel<HomeViewState, HomeEvent>(HomeViewState(), defaultDispatcher) {
        private var lastKnownStatusByDraftId: Map<String, RecordStatusTag> = emptyMap()

        init {
            observeSummary()
        }

        override fun dispatch(event: HomeEvent) {
            when (event) {
                HomeEvent.SyncConfirmationMessageShown -> updateState { it.copy(syncConfirmationMessage = null) }
            }
        }

        private fun observeSummary() {
            updateState { it.copy(status = UiStatus.Loading) }
            launchInViewModelScope {
                observeHomeSummaryUseCase()
                    .catch { error -> updateState { it.copy(status = loadErrorStatus(error)) } }
                    .collect { summary -> applySummary(summary) }
            }
        }

        /** Detects a draft transitioning from `AwaitingSync` to `Submitted` (automatic background sync). */
        private fun applySummary(summary: HomeSummary) {
            val newStatusByDraftId = summary.catchRecords.associate { it.id to it.status }
            val newlySynced =
                summary.catchRecords.filter { record ->
                    record.status == RecordStatusTag.Submitted &&
                        lastKnownStatusByDraftId[record.id] == RecordStatusTag.AwaitingSync
                }
            lastKnownStatusByDraftId = newStatusByDraftId
            updateState {
                it.copy(
                    status = UiStatus.Content(summary),
                    syncConfirmationMessage = syncConfirmationMessage(newlySynced) ?: it.syncConfirmationMessage,
                )
            }
        }

        /**
         * FR9: confirms a draft that was "Unsent" has now been submitted automatically (FR7);
         * `null` on an emission with no new transition (including the first, since
         * [lastKnownStatusByDraftId] starts empty so nothing can match `AwaitingSync`).
         */
        private fun syncConfirmationMessage(newlySynced: List<CatchRecordSummary>): String? {
            if (newlySynced.isEmpty()) return null
            return if (newlySynced.size == 1) {
                val reference =
                    newlySynced.single().catchRecordReference
                        ?: context.getString(R.string.records_reference_pending)
                context.resources.getQuantityString(R.plurals.sync_confirmation_message, 1, reference)
            } else {
                val count = newlySynced.size
                context.resources.getQuantityString(R.plurals.sync_confirmation_message, count, count)
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
