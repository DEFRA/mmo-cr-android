package uk.gov.defra.mmocatchrecord.feature.home.presentation

import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import timber.log.Timber
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.core.architecture.BaseViewModel
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
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
        init {
            observeSummary()
        }

        @Suppress("EmptyFunctionBlock")
        override fun dispatch(event: HomeEvent) {
        }

        private fun observeSummary() {
            updateState { it.copy(status = UiStatus.Loading) }
            launchInViewModelScope {
                observeHomeSummaryUseCase()
                    .catch { error -> updateState { it.copy(status = loadErrorStatus(error)) } }
                    .collect { summary -> updateState { it.copy(status = UiStatus.Content(summary)) } }
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
