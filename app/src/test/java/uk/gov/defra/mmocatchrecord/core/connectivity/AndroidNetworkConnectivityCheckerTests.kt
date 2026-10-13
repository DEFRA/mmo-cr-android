package uk.gov.defra.mmocatchrecord.core.connectivity

import android.content.Context
import android.content.ContextWrapper
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.robolectric.shadows.ShadowNetworkInfo

/** Covers [AndroidNetworkConnectivityChecker] -- no existing JVM test exercised this class before. */
@RunWith(RobolectricTestRunner::class)
class AndroidNetworkConnectivityCheckerTests {
    private val context = RuntimeEnvironment.getApplication()
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val shadowManager = shadowOf(connectivityManager)

    @Test
    fun `isConnected is false when there is no ConnectivityManager`() {
        val wrappedContext =
            object : ContextWrapper(context) {
                override fun getSystemService(name: String): Any? =
                    if (name == Context.CONNECTIVITY_SERVICE) null else super.getSystemService(name)
            }

        assertFalse(AndroidNetworkConnectivityChecker(wrappedContext).isConnected())
    }

    @Test
    fun `isConnected is false when there is no active network`() {
        shadowManager.setActiveNetworkInfo(null)
        shadowManager.setDefaultNetworkActive(false)

        assertFalse(AndroidNetworkConnectivityChecker(context).isConnected())
    }

    @Test
    fun `isConnected is false when the active network has no registered capabilities`() {
        val network = ShadowNetwork.newInstance(ConnectivityManager.TYPE_WIFI)
        val info =
            ShadowNetworkInfo.newInstance(
                NetworkInfo.DetailedState.CONNECTED,
                ConnectivityManager.TYPE_WIFI,
                0,
                true,
                true,
            )
        shadowManager.addNetwork(network, info)
        shadowManager.setActiveNetworkInfo(info)
        shadowManager.setDefaultNetworkActive(true)

        assertFalse(AndroidNetworkConnectivityChecker(context).isConnected())
    }

    @Test
    fun `isConnected is false when the active network lacks the internet capability`() {
        val network = ShadowNetwork.newInstance(ConnectivityManager.TYPE_WIFI)
        val info =
            ShadowNetworkInfo.newInstance(
                NetworkInfo.DetailedState.CONNECTED,
                ConnectivityManager.TYPE_WIFI,
                0,
                true,
                true,
            )
        shadowManager.addNetwork(network, info)
        shadowManager.setActiveNetworkInfo(info)
        shadowManager.setDefaultNetworkActive(true)
        shadowManager.setNetworkCapabilities(network, ShadowNetworkCapabilities.newInstance())

        assertFalse(AndroidNetworkConnectivityChecker(context).isConnected())
    }

    @Test
    fun `isConnected is true when the active network has the internet capability`() {
        val network = ShadowNetwork.newInstance(ConnectivityManager.TYPE_WIFI)
        val info =
            ShadowNetworkInfo.newInstance(
                NetworkInfo.DetailedState.CONNECTED,
                ConnectivityManager.TYPE_WIFI,
                0,
                true,
                true,
            )
        shadowManager.addNetwork(network, info)
        shadowManager.setActiveNetworkInfo(info)
        shadowManager.setDefaultNetworkActive(true)
        val capabilities = ShadowNetworkCapabilities.newInstance()
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowManager.setNetworkCapabilities(network, capabilities)

        assertTrue(AndroidNetworkConnectivityChecker(context).isConnected())
    }
}
