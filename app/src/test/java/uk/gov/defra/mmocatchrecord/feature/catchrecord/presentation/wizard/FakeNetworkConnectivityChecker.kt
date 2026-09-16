package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard

import uk.gov.defra.mmocatchrecord.core.connectivity.NetworkConnectivityChecker

/** Test double for [NetworkConnectivityChecker] — returns a fixed, test-controlled connectivity state. */
class FakeNetworkConnectivityChecker(
    private val connected: Boolean = true,
) : NetworkConnectivityChecker {
    override fun isConnected(): Boolean = connected
}
