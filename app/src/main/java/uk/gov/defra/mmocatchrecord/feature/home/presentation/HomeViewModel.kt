package uk.gov.defra.mmocatchrecord.feature.home.presentation

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import uk.gov.defra.mmocatchrecord.core.architecture.BaseViewModel
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.home.domain.GetHomeSummaryUseCase

/** ViewModel for the Home feature. Loads the summary on construction. */
class HomeViewModel(
    private val getHomeSummaryUseCase: GetHomeSummaryUseCase,
    defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : BaseViewModel<HomeViewState, HomeEvent>(
        initialState = HomeViewState(),
        defaultDispatcher = defaultDispatcher,
    ) {
    init {
        dispatch(HomeEvent.Load)
    }

    override fun dispatch(event: HomeEvent) {
        when (event) {
            HomeEvent.Load -> load()
        }
    }

    private fun load() {
        updateState { it.copy(status = UiStatus.Loading) }
        launchInViewModelScope {
            val result = getHomeSummaryUseCase()
            result.fold(
                onSuccess = { summary ->
                    updateState { it.copy(status = UiStatus.Content(summary)) }
                },
                onFailure = { error ->
                    updateState {
                        it.copy(
                            status =
                                UiStatus.Error(
                                    message = error.message ?: "Unable to load your home summary.",
                                    isRetryable = true,
                                ),
                        )
                    }
                },
            )
        }
    }
}
