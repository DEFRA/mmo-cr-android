package uk.gov.defra.mmocatchrecord.core.connectivity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Exposes [ConnectivityObserver] as Compose-observable `isOffline` state; [recheck] re-tests on demand (AC18).
 */
@HiltViewModel
class ConnectivityViewModel
    @Inject
    constructor(
        observer: ConnectivityObserver,
        private val connectivityChecker: NetworkConnectivityChecker,
    ) : ViewModel() {
        private val offline = MutableStateFlow(!observer.isOnline.value)
        val isOffline: StateFlow<Boolean> = offline.asStateFlow()

        init {
            viewModelScope.launch { observer.isOnline.collect { online -> offline.value = !online } }
        }

        fun recheck() {
            offline.value = !connectivityChecker.isConnected()
        }
    }
