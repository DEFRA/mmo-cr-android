package uk.gov.defra.mmocatchrecord.core.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

/**
 * Real [ConnectivityObserver] backed by [ConnectivityManager.registerNetworkCallback]. Treats any network
 * with internet capability as "online" — matches [AndroidNetworkConnectivityChecker]'s permissive check, so
 * the banner's online/offline state agrees with the submit-time check.
 */
class AndroidConnectivityObserver
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ConnectivityObserver {
        override val isOnline: Flow<Boolean>
            get() =
                callbackFlow {
                    val connectivityManager =
                        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

                    if (connectivityManager == null) {
                        trySend(false)
                        awaitClose {}
                        return@callbackFlow
                    }

                    val callback =
                        object : ConnectivityManager.NetworkCallback() {
                            override fun onAvailable(network: Network) {
                                trySend(hasInternet(connectivityManager))
                            }

                            override fun onLost(network: Network) {
                                trySend(hasInternet(connectivityManager))
                            }

                            override fun onCapabilitiesChanged(
                                network: Network,
                                networkCapabilities: NetworkCapabilities,
                            ) {
                                trySend(hasInternet(connectivityManager))
                            }

                            override fun onUnavailable() {
                                trySend(false)
                            }
                        }

                    // Emit the current state immediately so collectors don't wait for the first callback.
                    trySend(hasInternet(connectivityManager))

                    val request =
                        NetworkRequest
                            .Builder()
                            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                            .build()
                    connectivityManager.registerNetworkCallback(request, callback)

                    awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
                }.conflate().distinctUntilChanged()

        private fun hasInternet(connectivityManager: ConnectivityManager): Boolean {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }
