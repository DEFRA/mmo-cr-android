package uk.gov.defra.mmocatchrecord.feature.home.presentation

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
import uk.gov.defra.mmocatchrecord.feature.home.data.FakeHomeRepository
import uk.gov.defra.mmocatchrecord.feature.home.domain.GetHomeSummaryUseCase
import uk.gov.defra.mmocatchrecord.feature.home.domain.HomeSummary

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTests {
    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads summary on construction`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository =
                FakeHomeRepository(
                    result = Result.success(HomeSummary(signedInUserId = "alice", pendingCatchRecordCount = 3)),
                )
            val viewModel = HomeViewModel(GetHomeSummaryUseCase(repository), defaultDispatcher = dispatcher)

            viewModel.state.test {
                assertEquals(UiStatus.Loading, awaitItem().status)
                val content = awaitItem().status
                assertTrue(content is UiStatus.Content)
                assertEquals(3, (content as UiStatus.Content<HomeSummary>).value.pendingCatchRecordCount)
            }
        }

    @Test
    fun `repository failure surfaces a retryable error`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeHomeRepository(result = Result.failure(RuntimeException("offline")))
            val viewModel = HomeViewModel(GetHomeSummaryUseCase(repository), defaultDispatcher = dispatcher)

            viewModel.state.test {
                assertEquals(UiStatus.Loading, awaitItem().status)
                val error = awaitItem().status
                assertTrue(error is UiStatus.Error)
                assertTrue((error as UiStatus.Error).isRetryable)
            }
        }
}
