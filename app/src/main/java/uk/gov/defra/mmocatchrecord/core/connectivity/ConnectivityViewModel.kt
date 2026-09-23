package uk.gov.defra.mmocatchrecord.core.connectivity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Thin ViewModel wrapper exposing [ConnectivityObserver] as Compose-observable `isOffline` state — used by
 * every catch-record wizard screen (via `CatchRecordWizardScaffold`) to show/hide the persistent "Offline"
 * banner. Mirrors `AppLanguageViewModel`'s pattern: each screen obtains its own instance via
 * `hiltViewModel()`, all backed by the same singleton [ConnectivityObserver].
 */
@HiltViewModel
class ConnectivityViewModel
    @Inject
    constructor(
        observer: ConnectivityObserver,
    ) : ViewModel() {
        val isOffline: StateFlow<Boolean> =
            observer.isOnline
                .map { online -> !online }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = false,
                )

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
