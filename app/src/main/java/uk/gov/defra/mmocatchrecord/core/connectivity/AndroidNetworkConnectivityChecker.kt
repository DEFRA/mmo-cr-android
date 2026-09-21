package uk.gov.defra.mmocatchrecord.core.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Real [NetworkConnectivityChecker] backed by [ConnectivityManager]. Treats any active network with
 * either internet capability as "connected" (Wi-Fi, cellular, ethernet, VPN) — deliberately permissive,
 * since a false positive here only means the app *attempts* an online submit and falls back to the
 * offline/pending-sync path on failure (see `CatchRecordFlowViewModel.submitCatchRecord`), never data loss.
 */
class AndroidNetworkConnectivityChecker
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : NetworkConnectivityChecker {
        override fun isConnected(): Boolean {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    ?: return false
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }
