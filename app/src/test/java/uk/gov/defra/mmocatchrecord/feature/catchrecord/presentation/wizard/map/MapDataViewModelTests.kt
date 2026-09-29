package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.map

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.MapDataRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.MapDataUnavailableException
import uk.gov.defra.mmocatchrecord.mapdata.MapDataset

private class FakeMapDataRepository(
    private var result: Result<MapDataset>,
) : MapDataRepository {
    var loadCount = 0
        private set

    fun setNextResult(value: Result<MapDataset>) {
        result = value
    }

    override suspend fun loadDataset(): Result<MapDataset> {
        loadCount++
        return result
    }
}

private fun emptyDataset() = MapDataset(MapDataset.CURRENT_FORMAT_VERSION, emptyList(), emptyList(), emptyList())

@OptIn(ExperimentalCoroutinesApi::class)
class MapDataViewModelTests {
    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads the dataset once on init and exposes it as Content`() =
        runTest {
            val repository = FakeMapDataRepository(Result.success(emptyDataset()))
            val viewModel = MapDataViewModel(repository)

            viewModel.status.test {
                assertTrue(awaitItem() is UiStatus.Loading)
                val loaded = awaitItem()
                assertTrue(loaded is UiStatus.Content<MapDataset>)
            }
            assertTrue(repository.loadCount == 1)
        }

    @Test
    fun `surfaces a retryable error state when the repository load fails, never crashing`() =
        runTest {
            val repository =
                FakeMapDataRepository(Result.failure(MapDataUnavailableException("unavailable")))
            val viewModel = MapDataViewModel(repository)

            viewModel.status.test {
                assertTrue(awaitItem() is UiStatus.Loading)
                val errorState = awaitItem()
                assertTrue(errorState is UiStatus.Error)
                assertTrue((errorState as UiStatus.Error).isRetryable)
            }
        }

    @Test
    fun `retry re-invokes the repository and can recover from a prior failure`() =
        runTest {
            val repository =
                FakeMapDataRepository(Result.failure(MapDataUnavailableException("unavailable")))
            val viewModel = MapDataViewModel(repository)

            viewModel.status.test {
                awaitItem() // Loading
                awaitItem() // Error

                repository.setNextResult(Result.success(emptyDataset()))
                viewModel.retry()

                assertTrue(awaitItem() is UiStatus.Loading)
                assertTrue(awaitItem() is UiStatus.Content<MapDataset>)
            }
            assertTrue(repository.loadCount == 2)
        }
}
