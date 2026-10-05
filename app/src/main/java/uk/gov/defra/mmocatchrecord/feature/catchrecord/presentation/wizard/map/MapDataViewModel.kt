package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.MapDataRepository
import uk.gov.defra.mmocatchrecord.mapdata.MapDataset
import javax.inject.Inject

/**
 * Thin ViewModel wrapper loading the offline fisheries-map dataset once per process — mirrors
 * `ConnectivityViewModel`'s pattern: each [MapScreen] instance obtains its own via `hiltViewModel()`, all
 * backed by the same singleton [MapDataRepository] (which itself caches the parsed dataset). See
 * docs/development/offline-map.md.
 */
@HiltViewModel
class MapDataViewModel
    @Inject
    constructor(
        private val repository: MapDataRepository,
    ) : ViewModel() {
        private val _status = MutableStateFlow<UiStatus<MapDataset>>(UiStatus.Loading)
        val status: StateFlow<UiStatus<MapDataset>> = _status.asStateFlow()

        init {
            load()
        }

        fun retry() = load()

        private fun load() {
            _status.value = UiStatus.Loading
            viewModelScope.launch {
                repository
                    .loadDataset()
                    .onSuccess { dataset -> _status.value = UiStatus.Content(dataset) }
                    .onFailure { error ->
                        _status.value =
                            UiStatus.Error(
                                message = error.message ?: "Unable to load the offline map data",
                                isRetryable = true,
                            )
                    }
            }
        }
    }
