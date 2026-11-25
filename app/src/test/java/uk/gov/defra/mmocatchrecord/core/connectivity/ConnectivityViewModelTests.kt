package uk.gov.defra.mmocatchrecord.core.connectivity

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectivityViewModelTests {
    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `isOffline is false when the observer reports online`() =
        runTest {
            val viewModel = ConnectivityViewModel(FakeConnectivityObserver(initiallyOnline = true)) { true }

            viewModel.isOffline.test {
                assertFalse(awaitItem())
            }
        }

    @Test
    fun `isOffline is true immediately when the app starts with no connectivity`() =
        runTest {
            val viewModel = ConnectivityViewModel(FakeConnectivityObserver(initiallyOnline = false)) { true }

            assertTrue(viewModel.isOffline.value)
            viewModel.isOffline.test {
                assertTrue(awaitItem())
            }
        }

    @Test
    fun `isOffline reacts to connectivity changing after start`() =
        runTest {
            val observer = FakeConnectivityObserver(initiallyOnline = true)
            val viewModel = ConnectivityViewModel(observer) { true }

            viewModel.isOffline.test {
                assertFalse(awaitItem())

                observer.setOnline(false)
                assertTrue(awaitItem())

                observer.setOnline(true)
                assertFalse(awaitItem())
            }
        }

    @Test
    fun `recheck re-tests the connection on demand`() =
        runTest {
            val observer = FakeConnectivityObserver(initiallyOnline = false)
            var connected = true
            val viewModel = ConnectivityViewModel(observer) { connected }

            viewModel.recheck()
            assertFalse(viewModel.isOffline.value)

            connected = false
            viewModel.recheck()
            assertTrue(viewModel.isOffline.value)
        }

    @Test
    fun `the next observer change overrides a recheck result`() =
        runTest {
            val observer = FakeConnectivityObserver(initiallyOnline = false)
            val viewModel = ConnectivityViewModel(observer) { false }

            advanceUntilIdle()
            viewModel.recheck()
            observer.setOnline(true)
            advanceUntilIdle()

            assertFalse(viewModel.isOffline.value)
        }
}
