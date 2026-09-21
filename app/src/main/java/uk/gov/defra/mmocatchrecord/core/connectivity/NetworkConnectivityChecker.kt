package uk.gov.defra.mmocatchrecord.core.connectivity

/**
 * Read-only check of whether the device currently has network connectivity. Deliberately a plain
 * synchronous boolean (not a `Flow`) — Phase 8's submit action only needs a point-in-time check at the
 * moment "Accept and submit trip details" is pressed, not ongoing observation; see
 * [AndroidNetworkConnectivityChecker] and ADR 0009.
 */
interface NetworkConnectivityChecker {
    fun isConnected(): Boolean
}
