package uk.gov.defra.mmocatchrecord.core.connectivity

import android.content.Context
import android.content.ContextWrapper
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

// Shadow state is static, so it is reset before each test to stop a registered callback leaking across tests.
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

    private fun capabilities(
        internet: Boolean,
        validated: Boolean,
    ): NetworkCapabilities {
        val capabilities = ShadowNetworkCapabilities.newInstance()
        if (internet) shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if (validated) shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return capabilities
    }

    private fun setActiveNetwork(
        internet: Boolean = true,
        validated: Boolean = true,
    ): Network {
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
        shadowManager.setNetworkCapabilities(network, capabilities(internet, validated))
        return network
    }

    private fun setNoActiveNetwork() {
        shadowManager.setActiveNetworkInfo(null)
        shadowManager.setDefaultNetworkActive(false)
    }

    private var callbacksBeforeStart: Set<ConnectivityManager.NetworkCallback> = emptySet()

    private fun TestScope.startObserver(ctx: Context = context): AndroidConnectivityObserver {
        callbacksBeforeStart = shadowManager.networkCallbacks.toSet()
        val observer = AndroidConnectivityObserver(ctx, backgroundScope)
        runCurrent()
        return observer
    }

    private fun registeredCallback(): ConnectivityManager.NetworkCallback =
        (shadowManager.networkCallbacks - callbacksBeforeStart).single()

    @Test
    fun `isOnline is true at start when the active network has validated internet`() =
        runTest {
            setActiveNetwork()

            assertTrue(startObserver().isOnline.value)
        }

    @Test
    fun `isOnline is false at start when there is no active network`() =
        runTest {
            setNoActiveNetwork()

            assertFalse(startObserver().isOnline.value)
        }

    @Test
    fun `isOnline is false at start when internet is present but not validated`() =
        runTest {
            setActiveNetwork(validated = false)

            assertFalse(startObserver().isOnline.value)
        }

    @Test
    fun `isOnline is false at start when the active network has no registered capabilities`() =
        runTest {
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

            assertFalse(startObserver().isOnline.value)
        }

    @Test
    fun `isOnline goes offline on onLost even while activeNetwork still reports the lost network`() =
        runTest {
            val network = setActiveNetwork()
            val observer = startObserver()

            observer.isOnline.test {
                assertEquals(true, awaitItem())
                registeredCallback().onLost(network)
                assertEquals(false, awaitItem())
            }
        }

    @Test
    fun `isOnline goes online when the default network gains validated internet`() =
        runTest {
            setNoActiveNetwork()
            val observer = startObserver()

            observer.isOnline.test {
                assertEquals(false, awaitItem())
                val network = ShadowNetwork.newInstance(ConnectivityManager.TYPE_WIFI)
                registeredCallback().onCapabilitiesChanged(network, capabilities(internet = true, validated = true))
                assertEquals(true, awaitItem())
            }
        }

    @Test
    fun `isOnline goes offline when the default network loses validation`() =
        runTest {
            val network = setActiveNetwork()
            val observer = startObserver()

            observer.isOnline.test {
                assertEquals(true, awaitItem())
                registeredCallback().onCapabilitiesChanged(network, capabilities(internet = true, validated = false))
                assertEquals(false, awaitItem())
            }
        }

    @Test
    fun `isOnline goes offline when the callback reports onUnavailable`() =
        runTest {
            setActiveNetwork()
            val observer = startObserver()

            observer.isOnline.test {
                assertEquals(true, awaitItem())
                registeredCallback().onUnavailable()
                assertEquals(false, awaitItem())
            }
        }

    @Test
    fun `isOnline is false and registers no callback when there is no ConnectivityManager`() =
        runTest {
            val wrappedContext =
                object : ContextWrapper(context) {
                    override fun getSystemService(name: String): Any? =
                        if (name == Context.CONNECTIVITY_SERVICE) null else super.getSystemService(name)
                }

            assertFalse(startObserver(wrappedContext).isOnline.value)
            assertTrue((shadowManager.networkCallbacks - callbacksBeforeStart).isEmpty())
        }
}
