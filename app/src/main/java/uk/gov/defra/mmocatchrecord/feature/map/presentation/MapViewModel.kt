package uk.gov.defra.mmocatchrecord.feature.map.presentation

import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import uk.gov.defra.mmocatchrecord.core.architecture.BaseViewModel
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.map.domain.GetCatchLocationsUseCase
import javax.inject.Inject

/** ViewModel for the Map feature. Loads catch locations on construction. */
@HiltViewModel
class MapViewModel
    @Inject
    constructor(
        private val getCatchLocationsUseCase: GetCatchLocationsUseCase,
        defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    ) : BaseViewModel<MapViewState, MapEvent>(
            initialState = MapViewState(),
            defaultDispatcher = defaultDispatcher,
        ) {
        init {
            dispatch(MapEvent.Load)
        }

        override fun dispatch(event: MapEvent) {
            when (event) {
                MapEvent.Load -> load()
            }
        }

        private fun load() {
            updateState { it.copy(status = UiStatus.Loading) }
            launchInViewModelScope {
                val result = getCatchLocationsUseCase()
                result.fold(
                    onSuccess = { locations -> updateState { it.copy(status = UiStatus.Content(locations)) } },
                    onFailure = { error ->
                        updateState {
                            it.copy(
                                status =
                                    UiStatus.Error(
                                        message = error.message ?: "Unable to load catch locations.",
                                        isRetryable = true,
                                    ),
                            )
                        }
                    },
                )
            }
        }
    }
