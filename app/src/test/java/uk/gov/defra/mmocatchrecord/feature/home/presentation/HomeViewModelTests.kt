package uk.gov.defra.mmocatchrecord.feature.home.presentation

import android.content.Context
import app.cash.turbine.test
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.common.design.RecordStatusTag
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DmyDate
import uk.gov.defra.mmocatchrecord.feature.home.data.FakeHomeRepository
import uk.gov.defra.mmocatchrecord.feature.home.domain.CatchRecordSummary
import uk.gov.defra.mmocatchrecord.feature.home.domain.HomeSummary
import uk.gov.defra.mmocatchrecord.feature.home.domain.ObserveHomeSummaryUseCase

private fun recordWithEveryStatus() =
    listOf(
        CatchRecordSummary("1", "MMO-REF-001", "vessel-1", DmyDate(1, 1, 2026), RecordStatusTag.Submitted),
        CatchRecordSummary("2", "MMO-REF-002", "vessel-1", DmyDate(2, 1, 2026), RecordStatusTag.ReadyToSubmit),
        CatchRecordSummary("3", null, "vessel-1", null, RecordStatusTag.Draft),
        CatchRecordSummary("4", "MMO-REF-004", "vessel-1", DmyDate(4, 1, 2026), RecordStatusTag.AwaitingSync),
    )

private fun buildViewModel(
    repository: FakeHomeRepository,
    context: Context = mock(),
    dispatcher: CoroutineDispatcher = StandardTestDispatcher(),
): HomeViewModel = HomeViewModel(ObserveHomeSummaryUseCase(repository), context, dispatcher)

/** Covers AC5 (all four statuses), reactive re-emission and the error path (ADR 0014 Phase B). */
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
    fun `loads summary reactively on construction`() =
        runTest {
            val repository = FakeHomeRepository()
            repository.emit(HomeSummary(signedInUserId = "alice", catchRecords = recordWithEveryStatus()))
            val viewModel = buildViewModel(repository)

            viewModel.state.test {
                assertEquals(UiStatus.Loading, awaitItem().status)
                val content = awaitItem().status
                assertTrue(content is UiStatus.Content)
            }
        }

    @Test
    fun `AC5 all four record statuses map through and discarded cannot leak in`() =
        runTest {
            val repository = FakeHomeRepository()
            repository.emit(HomeSummary(signedInUserId = "alice", catchRecords = recordWithEveryStatus()))
            val viewModel = buildViewModel(repository)

            viewModel.state.test {
                awaitItem()
                val content = (awaitItem().status as UiStatus.Content).value
                val statuses = content.catchRecords.map { it.status }.toSet()
                assertEquals(
                    setOf(
                        RecordStatusTag.Submitted,
                        RecordStatusTag.ReadyToSubmit,
                        RecordStatusTag.Draft,
                        RecordStatusTag.AwaitingSync,
                    ),
                    statuses,
                )
                assertEquals(1, content.pendingCatchRecordCount)
            }
        }

    @Test
    fun `a null tripEndDate is accepted without error`() =
        runTest {
            val repository = FakeHomeRepository()
            val draftOnly =
                HomeSummary(
                    signedInUserId = "alice",
                    catchRecords = listOf(CatchRecordSummary("1", null, "vessel-1", null, RecordStatusTag.Draft)),
                )
            repository.emit(draftOnly)
            val viewModel = buildViewModel(repository)

            viewModel.state.test {
                awaitItem()
                val content = (awaitItem().status as UiStatus.Content).value
                assertEquals(null, content.catchRecords.single().tripEndDate)
            }
        }

    @Test
    fun `reactively re-emits when the repository emits again`() =
        runTest {
            val repository = FakeHomeRepository()
            val first = HomeSummary(signedInUserId = "alice", catchRecords = emptyList())
            repository.emit(first)
            val viewModel = buildViewModel(repository)

            viewModel.state.test {
                awaitItem()
                assertEquals(0, (awaitItem().status as UiStatus.Content).value.catchRecords.size)

                val second =
                    HomeSummary(
                        signedInUserId = "alice",
                        catchRecords =
                            listOf(
                                CatchRecordSummary("1", "MMO-REF-001", "v1", null, RecordStatusTag.Draft),
                            ),
                    )
                repository.emit(second)
                assertEquals(1, (awaitItem().status as UiStatus.Content).value.catchRecords.size)
            }
        }

    @Test
    fun `repository failure surfaces a safe retryable error message`() =
        runTest {
            val context = mock<Context>()
            whenever(context.getString(R.string.home_summary_load_error)).thenReturn("We could not load records")
            val repository = FakeHomeRepository()
            repository.emitError(RuntimeException("offline"))
            val viewModel = buildViewModel(repository, context)

            viewModel.state.test {
                awaitItem()
                val error = awaitItem().status
                assertTrue(error is UiStatus.Error)
                assertEquals("We could not load records", (error as UiStatus.Error).message)
                assertTrue(error.isRetryable)
                assertFalse(error.message.contains("offline"))
            }
        }
}
