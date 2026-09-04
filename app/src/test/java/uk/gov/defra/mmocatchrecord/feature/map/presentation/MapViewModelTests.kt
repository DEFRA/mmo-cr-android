package uk.gov.defra.mmocatchrecord.feature.map.presentation

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.map.data.FakeMapRepository
import uk.gov.defra.mmocatchrecord.feature.map.domain.CatchLocation
import uk.gov.defra.mmocatchrecord.feature.map.domain.GetCatchLocationsUseCase

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTests {
    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads catch locations on construction`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val location = CatchLocation(id = "1", latitude = 50.0, longitude = -1.0, label = "Solent")
            val repository = FakeMapRepository(result = Result.success(listOf(location)))
            val viewModel = MapViewModel(GetCatchLocationsUseCase(repository), defaultDispatcher = dispatcher)

            viewModel.state.test {
                assertEquals(UiStatus.Loading, awaitItem().status)
                val content = awaitItem().status
                assertTrue(content is UiStatus.Content)
                assertEquals(listOf(location), (content as UiStatus.Content<List<CatchLocation>>).value)
            }
        }

    @Test
    fun `repository failure surfaces a retryable error`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeMapRepository(result = Result.failure(RuntimeException("offline")))
            val viewModel = MapViewModel(GetCatchLocationsUseCase(repository), defaultDispatcher = dispatcher)

            viewModel.state.test {
                assertEquals(UiStatus.Loading, awaitItem().status)
                val error = awaitItem().status
                assertTrue(error is UiStatus.Error)
                assertTrue((error as UiStatus.Error).isRetryable)
            }
        }
}
