package uk.gov.defra.mmocatchrecord.core.connectivity

import android.content.Context
import android.content.ContextWrapper
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowConnectivityManager
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.robolectric.shadows.ShadowNetworkInfo

/** Robolectric shadows simulate ConnectivityManager states without device-only connectivity dependencies.
 * Reset the shadow before each test because its static state can leak callbacks. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AndroidConnectivityObserverTests {
    private val context = RuntimeEnvironment.getApplication()
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val shadowManager = shadowOf(connectivityManager)

    @Before
    fun resetShadowConnectivityState() {
        ShadowConnectivityManager.reset()
    }

    private fun networkInfo(connected: Boolean): NetworkInfo =
        ShadowNetworkInfo.newInstance(
            if (connected) NetworkInfo.DetailedState.CONNECTED else NetworkInfo.DetailedState.DISCONNECTED,
            ConnectivityManager.TYPE_WIFI,
            0,
            connected,
            connected,
        )

    /** Registers an active Wi-Fi network, optionally with (or without) the internet capability. */
    private fun setActiveNetwork(hasInternetCapability: Boolean): Network {
        val network = ShadowNetwork.newInstance(ConnectivityManager.TYPE_WIFI)
        val info = networkInfo(connected = true)
        shadowManager.addNetwork(network, info)
        shadowManager.setActiveNetworkInfo(info)
        shadowManager.setDefaultNetworkActive(true)
        val capabilities = ShadowNetworkCapabilities.newInstance()
        if (hasInternetCapability) shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowManager.setNetworkCapabilities(network, capabilities)
        return network
    }

    private fun setNoActiveNetwork() {
        shadowManager.setActiveNetworkInfo(null)
        shadowManager.setDefaultNetworkActive(false)
    }

    @Test
    fun `isOnline emits true immediately when an active network already has internet capability`() =
        runTest {
            setActiveNetwork(hasInternetCapability = true)
            val observer = AndroidConnectivityObserver(context)

            observer.isOnline.test {
                assertEquals(true, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `isOnline emits false when there is no active network`() =
        runTest {
            setNoActiveNetwork()
            val observer = AndroidConnectivityObserver(context)

            observer.isOnline.test {
                assertEquals(false, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `isOnline emits false when the active network has no registered capabilities`() =
        runTest {
            val network = ShadowNetwork.newInstance(ConnectivityManager.TYPE_WIFI)
            val info = networkInfo(connected = true)
            shadowManager.addNetwork(network, info)
            shadowManager.setActiveNetworkInfo(info)
            shadowManager.setDefaultNetworkActive(true)
            val observer = AndroidConnectivityObserver(context)

            observer.isOnline.test {
                assertEquals(false, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `isOnline transitions to false when the network is lost`() =
        runTest {
            val network = setActiveNetwork(hasInternetCapability = true)
            val observer = AndroidConnectivityObserver(context)

            observer.isOnline.test {
                assertEquals(true, awaitItem())
                val callback = shadowManager.networkCallbacks.single()
                setNoActiveNetwork()
                callback.onLost(network)
                assertEquals(false, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `isOnline transitions to true when a network becomes available`() =
        runTest {
            setNoActiveNetwork()
            val observer = AndroidConnectivityObserver(context)

            observer.isOnline.test {
                assertEquals(false, awaitItem())
                val callback = shadowManager.networkCallbacks.single()
                val network = setActiveNetwork(hasInternetCapability = true)
                callback.onAvailable(network)
                assertEquals(true, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `isOnline reflects onCapabilitiesChanged losing internet capability`() =
        runTest {
            val network = setActiveNetwork(hasInternetCapability = true)
            val observer = AndroidConnectivityObserver(context)

            observer.isOnline.test {
                assertEquals(true, awaitItem())
                val callback = shadowManager.networkCallbacks.single()
                val noInternetCapabilities = ShadowNetworkCapabilities.newInstance()
                shadowManager.setNetworkCapabilities(network, noInternetCapabilities)
                callback.onCapabilitiesChanged(network, noInternetCapabilities)
                assertEquals(false, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `isOnline emits false unconditionally when the callback reports onUnavailable`() =
        runTest {
            setActiveNetwork(hasInternetCapability = true)
            val observer = AndroidConnectivityObserver(context)

            observer.isOnline.test {
                assertEquals(true, awaitItem())
                val callback = shadowManager.networkCallbacks.single()
                // The real network is still online -- onUnavailable must still force a `false` emission.
                callback.onUnavailable()
                assertEquals(false, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `isOnline emits false and never registers a callback when there is no ConnectivityManager`() =
        runTest {
            val wrappedContext =
                object : ContextWrapper(context) {
                    override fun getSystemService(name: String): Any? =
                        if (name == Context.CONNECTIVITY_SERVICE) null else super.getSystemService(name)
                }
            val observer = AndroidConnectivityObserver(wrappedContext)

            observer.isOnline.test {
                assertEquals(false, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
