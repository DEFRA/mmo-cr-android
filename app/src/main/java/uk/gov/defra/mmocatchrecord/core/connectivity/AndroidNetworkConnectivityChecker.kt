package uk.gov.defra.mmocatchrecord.core.connectivity

import android.content.Context
import android.net.ConnectivityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Point-in-time check using the same validated-internet rule as [AndroidConnectivityObserver], so the
 *  submit/reference-data offline paths agree with the banner; a false positive still falls back offline. */
class AndroidNetworkConnectivityChecker
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : NetworkConnectivityChecker {
        override fun isConnected(): Boolean {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    ?: return false
            return connectivityManager.isCurrentlyOnline()
        }
    }
