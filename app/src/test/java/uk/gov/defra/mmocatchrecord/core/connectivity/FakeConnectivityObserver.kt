package uk.gov.defra.mmocatchrecord.core.connectivity

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Test double for [ConnectivityObserver] — a controllable [Flow] the test can push online/offline onto. */
class FakeConnectivityObserver(
    initiallyOnline: Boolean = true,
) : ConnectivityObserver {
    private val online = MutableStateFlow(initiallyOnline)

    override val isOnline: Flow<Boolean> = online

    fun setOnline(value: Boolean) {
        online.value = value
    }
}
