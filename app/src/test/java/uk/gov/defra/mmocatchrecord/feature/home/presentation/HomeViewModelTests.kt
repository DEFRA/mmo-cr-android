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
import uk.gov.defra.mmocatchrecord.common.design.DebugSettingsSection
import uk.gov.defra.mmocatchrecord.common.design.RecordStatusTag
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DmyDate
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.RetryCatchRecordSubmissionUseCase
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeCatchRecordSyncScheduler
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeNetworkConnectivityChecker
import uk.gov.defra.mmocatchrecord.feature.home.data.FakeHomeRepository
import uk.gov.defra.mmocatchrecord.feature.home.domain.CatchRecordSummary
import uk.gov.defra.mmocatchrecord.feature.home.domain.HomeSummary
import uk.gov.defra.mmocatchrecord.feature.home.domain.ObserveHomeSummaryUseCase

/** No-op test double — the real debug/release bindings live in build-type source sets, not `src/test`. */
private object NoOpDebugSettingsSectionFake : DebugSettingsSection {
    @androidx.compose.runtime.Composable
    override fun Render() = Unit
}

private fun recordWithEveryStatus() =
    listOf(
        CatchRecordSummary("1", "MMO-REF-001", "vessel-1", DmyDate(1, 1, 2026), RecordStatusTag.Submitted),
        CatchRecordSummary("2", "MMO-REF-002", "vessel-1", DmyDate(2, 1, 2026), RecordStatusTag.ReadyToSubmit),
        CatchRecordSummary("3", null, "vessel-1", null, RecordStatusTag.Draft),
        CatchRecordSummary("4", "MMO-REF-004", "vessel-1", DmyDate(4, 1, 2026), RecordStatusTag.AwaitingSync),
    )

/** Stubs [Context.getResources] with a mock, since Mockito does not deep-stub a plain `mock<Context>()`. */
private fun contextWithQuantityString(
    quantity: Int,
    vararg formatArgs: Any,
    result: String,
): Context {
    val resources = mock<android.content.res.Resources>()
    val pluralsRes = uk.gov.defra.mmocatchrecord.R.plurals.sync_confirmation_message
    whenever(resources.getQuantityString(pluralsRes, quantity, *formatArgs)).thenReturn(result)
    val context = mock<Context>()
    whenever(context.resources).thenReturn(resources)
    return context
}

@Suppress("LongParameterList")
private fun buildViewModel(
    repository: FakeHomeRepository,
    context: Context = mock(),
    syncScheduler: FakeCatchRecordSyncScheduler = FakeCatchRecordSyncScheduler(),
    connectivityChecker: FakeNetworkConnectivityChecker = FakeNetworkConnectivityChecker(),
    dispatcher: CoroutineDispatcher = StandardTestDispatcher(),
): HomeViewModel =
    HomeViewModel(
        ObserveHomeSummaryUseCase(repository),
        RetryCatchRecordSubmissionUseCase(syncScheduler, connectivityChecker),
        NoOpDebugSettingsSectionFake,
        context,
        dispatcher,
    )

/** Covers AC5 (all four statuses), reactive re-emission, the error path and AC4 retry (ADR 0014 Phase B/C). */
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

    @Test
    fun `AC4 retry submission enqueues exactly once and disables the row`() =
        runTest {
            val repository = FakeHomeRepository()
            repository.emit(HomeSummary(signedInUserId = "alice", catchRecords = recordWithEveryStatus()))
            val syncScheduler = FakeCatchRecordSyncScheduler()
            val viewModel = buildViewModel(repository, syncScheduler = syncScheduler)

            viewModel.state.test {
                awaitItem()
                awaitItem()
                viewModel.dispatch(HomeEvent.RetrySubmission("4"))
                assertEquals(listOf("4"), syncScheduler.scheduledDraftIds)
                assertTrue("4" in awaitItem().retryingIds)
            }
        }

    @Test
    fun `double-dispatching retry for the same draft only enqueues once`() =
        runTest {
            val repository = FakeHomeRepository()
            repository.emit(HomeSummary(signedInUserId = "alice", catchRecords = recordWithEveryStatus()))
            val syncScheduler = FakeCatchRecordSyncScheduler()
            val viewModel = buildViewModel(repository, syncScheduler = syncScheduler)

            viewModel.state.test {
                awaitItem()
                awaitItem()
                viewModel.dispatch(HomeEvent.RetrySubmission("4"))
                awaitItem()
                viewModel.dispatch(HomeEvent.RetrySubmission("4"))
                expectNoEvents()
                assertEquals(listOf("4"), syncScheduler.scheduledDraftIds)
            }
        }

    @Test
    fun `retrying while offline still enqueues and emits the offline message`() =
        runTest {
            val context = mock<Context>()
            whenever(context.getString(R.string.records_retry_offline_message)).thenReturn("offline-message")
            val repository = FakeHomeRepository()
            repository.emit(HomeSummary(signedInUserId = "alice", catchRecords = recordWithEveryStatus()))
            val syncScheduler = FakeCatchRecordSyncScheduler()
            val connectivityChecker = FakeNetworkConnectivityChecker(connected = false)
            val viewModel =
                buildViewModel(
                    repository,
                    context = context,
                    syncScheduler = syncScheduler,
                    connectivityChecker = connectivityChecker,
                )

            viewModel.state.test {
                awaitItem()
                awaitItem()
                viewModel.dispatch(HomeEvent.RetrySubmission("4"))
                val afterRetry = awaitItem()
                assertEquals(listOf("4"), syncScheduler.scheduledDraftIds)
                assertEquals("offline-message", afterRetry.offlineRetryMessage)

                viewModel.dispatch(HomeEvent.OfflineRetryMessageShown)
                assertEquals(null, awaitItem().offlineRetryMessage)
            }
        }

    @Test
    fun `retryingIds clears once the draft's status actually changes`() =
        runTest {
            val context =
                contextWithQuantityString(1, "MMO-REF-004", result = "Catch record MMO-REF-004 has been submitted")
            val repository = FakeHomeRepository()
            repository.emit(HomeSummary(signedInUserId = "alice", catchRecords = recordWithEveryStatus()))
            val viewModel = buildViewModel(repository, context = context)

            viewModel.state.test {
                awaitItem()
                awaitItem()
                viewModel.dispatch(HomeEvent.RetrySubmission("4"))
                assertTrue("4" in awaitItem().retryingIds)

                val submitted =
                    recordWithEveryStatus().map {
                        if (it.id == "4") it.copy(status = RecordStatusTag.Submitted) else it
                    }
                repository.emit(HomeSummary(signedInUserId = "alice", catchRecords = submitted))
                assertFalse("4" in awaitItem().retryingIds)
            }
        }

    @Test
    fun `FR9 an AwaitingSync to Submitted transition surfaces a singular confirmation message`() =
        runTest {
            val context =
                contextWithQuantityString(1, "MMO-REF-004", result = "Catch record MMO-REF-004 has been submitted")
            val repository = FakeHomeRepository()
            repository.emit(HomeSummary(signedInUserId = "alice", catchRecords = recordWithEveryStatus()))
            val viewModel = buildViewModel(repository, context = context)

            viewModel.state.test {
                awaitItem()
                awaitItem()

                val synced =
                    recordWithEveryStatus().map {
                        if (it.id == "4") it.copy(status = RecordStatusTag.Submitted) else it
                    }
                repository.emit(HomeSummary(signedInUserId = "alice", catchRecords = synced))
                assertEquals("Catch record MMO-REF-004 has been submitted", awaitItem().syncConfirmationMessage)
            }
        }

    @Test
    fun `FR9 a singular confirmation falls back to a pending-reference label when the reference is null`() =
        runTest {
            val resources = mock<android.content.res.Resources>()
            val pluralsRes = R.plurals.sync_confirmation_message
            whenever(resources.getQuantityString(pluralsRes, 1, "Reference not yet assigned"))
                .thenReturn("Catch record Reference not yet assigned has been submitted")
            val context = mock<Context>()
            whenever(context.resources).thenReturn(resources)
            whenever(context.getString(R.string.records_reference_pending)).thenReturn("Reference not yet assigned")

            val repository = FakeHomeRepository()
            val awaitingOne =
                listOf(CatchRecordSummary("4", null, "vessel-1", DmyDate(4, 1, 2026), RecordStatusTag.AwaitingSync))
            repository.emit(HomeSummary(signedInUserId = "alice", catchRecords = awaitingOne))
            val viewModel = buildViewModel(repository, context = context)

            viewModel.state.test {
                awaitItem()
                awaitItem()

                val synced = awaitingOne.map { it.copy(status = RecordStatusTag.Submitted) }
                repository.emit(HomeSummary(signedInUserId = "alice", catchRecords = synced))
                assertEquals(
                    "Catch record Reference not yet assigned has been submitted",
                    awaitItem().syncConfirmationMessage,
                )
            }
        }

    @Test
    fun `FR9 multiple simultaneous transitions surface a plural confirmation message`() =
        runTest {
            val context = contextWithQuantityString(2, 2, result = "2 catch records have been submitted")
            val repository = FakeHomeRepository()
            val awaitingTwo =
                listOf(
                    CatchRecordSummary(
                        "4",
                        "MMO-REF-004",
                        "vessel-1",
                        DmyDate(4, 1, 2026),
                        RecordStatusTag.AwaitingSync,
                    ),
                    CatchRecordSummary(
                        "5",
                        "MMO-REF-005",
                        "vessel-1",
                        DmyDate(5, 1, 2026),
                        RecordStatusTag.AwaitingSync,
                    ),
                )
            repository.emit(HomeSummary(signedInUserId = "alice", catchRecords = awaitingTwo))
            val viewModel = buildViewModel(repository, context = context)

            viewModel.state.test {
                awaitItem()
                awaitItem()

                val synced = awaitingTwo.map { it.copy(status = RecordStatusTag.Submitted) }
                repository.emit(HomeSummary(signedInUserId = "alice", catchRecords = synced))
                assertEquals("2 catch records have been submitted", awaitItem().syncConfirmationMessage)
            }
        }

    @Test
    fun `FR9 cold start with an already-Submitted record does not surface a confirmation message`() =
        runTest {
            val repository = FakeHomeRepository()
            repository.emit(HomeSummary(signedInUserId = "alice", catchRecords = recordWithEveryStatus()))
            val viewModel = buildViewModel(repository)

            viewModel.state.test {
                awaitItem()
                assertEquals(null, awaitItem().syncConfirmationMessage)
            }
        }

    @Test
    fun `FR9 dismissing the sync confirmation message clears it`() =
        runTest {
            val context =
                contextWithQuantityString(1, "MMO-REF-004", result = "Catch record MMO-REF-004 has been submitted")
            val repository = FakeHomeRepository()
            repository.emit(HomeSummary(signedInUserId = "alice", catchRecords = recordWithEveryStatus()))
            val viewModel = buildViewModel(repository, context = context)

            viewModel.state.test {
                awaitItem()
                awaitItem()

                val synced =
                    recordWithEveryStatus().map {
                        if (it.id == "4") it.copy(status = RecordStatusTag.Submitted) else it
                    }
                repository.emit(HomeSummary(signedInUserId = "alice", catchRecords = synced))
                assertEquals("Catch record MMO-REF-004 has been submitted", awaitItem().syncConfirmationMessage)

                viewModel.dispatch(HomeEvent.SyncConfirmationMessageShown)
                assertEquals(null, awaitItem().syncConfirmationMessage)
            }
        }
}
