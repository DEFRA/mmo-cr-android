package uk.gov.defra.mmocatchrecord.core.connectivity

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Test double for [ConnectivityObserver] — a controllable [StateFlow] the test can push online/offline onto. */
class FakeConnectivityObserver(
    initiallyOnline: Boolean = true,
) : ConnectivityObserver {
    private val online = MutableStateFlow(initiallyOnline)

    override val isOnline: StateFlow<Boolean> = online

    fun setOnline(value: Boolean) {
        online.value = value
    }
}
