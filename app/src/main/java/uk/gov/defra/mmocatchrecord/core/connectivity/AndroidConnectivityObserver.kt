package uk.gov.defra.mmocatchrecord.core.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn

/** Process-wide hot [StateFlow] fed by the default-network callback. Uses callback args only: querying
 *  `activeNetwork` inside a callback races (lost network still reported active) and misses going offline. */
class AndroidConnectivityObserver(
    context: Context,
    scope: CoroutineScope,
) : ConnectivityObserver {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    override val isOnline: StateFlow<Boolean> =
        callbackFlow {
            val manager = connectivityManager
            if (manager == null) {
                trySend(false)
                awaitClose {}
                return@callbackFlow
            }

            val callback =
                object : ConnectivityManager.NetworkCallback() {
                    override fun onCapabilitiesChanged(
                        network: Network,
                        networkCapabilities: NetworkCapabilities,
                    ) {
                        trySend(networkCapabilities.hasValidatedInternet())
                    }

                    override fun onLost(network: Network) {
                        trySend(false)
                    }

                    override fun onUnavailable() {
                        trySend(false)
                    }
                }

            trySend(manager.isCurrentlyOnline())
            manager.registerDefaultNetworkCallback(callback)
            awaitClose { manager.unregisterNetworkCallback(callback) }
        }.conflate()
            .distinctUntilChanged()
            .stateIn(scope, SharingStarted.Eagerly, connectivityManager?.isCurrentlyOnline() ?: false)
}

internal fun ConnectivityManager.isCurrentlyOnline(): Boolean {
    val network = activeNetwork ?: return false
    return getNetworkCapabilities(network)?.hasValidatedInternet() ?: false
}

private fun NetworkCapabilities.hasValidatedInternet(): Boolean =
    hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
