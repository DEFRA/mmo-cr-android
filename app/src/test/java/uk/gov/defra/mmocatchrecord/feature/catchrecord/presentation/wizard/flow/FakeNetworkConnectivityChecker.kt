package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow

import uk.gov.defra.mmocatchrecord.core.connectivity.NetworkConnectivityChecker

/** Test double for [NetworkConnectivityChecker] — returns a fixed, test-controlled connectivity state. */
class FakeNetworkConnectivityChecker(
    private val connected: Boolean = true,
) : NetworkConnectivityChecker {
    /** Throws [throwOnNextIsConnected] from the next [isConnected] call instead of returning, then clears
     *  itself — simulates an unexpected exception bypassing the caller's `.fold`. */
    var throwOnNextIsConnected: Throwable? = null

    override fun isConnected(): Boolean {
        throwOnNextIsConnected?.let {
            throwOnNextIsConnected = null
            throw it
        }
        return connected
    }
}
