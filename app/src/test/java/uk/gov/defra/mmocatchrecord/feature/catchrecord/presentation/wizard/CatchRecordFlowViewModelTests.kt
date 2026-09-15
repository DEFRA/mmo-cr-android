@file:Suppress("detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DmyDate

@OptIn(ExperimentalCoroutinesApi::class)
class CatchRecordFlowViewModelTests {
    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(
        repository: FakeCatchRecordDraftRepository,
        referenceDataRepository: FakeReferenceDataRepository = FakeReferenceDataRepository(),
        clock: () -> Long = { 0L },
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
    ) = CatchRecordFlowViewModel(repository, referenceDataRepository, clock, dispatcher)

    @Test
    fun `enter flow with no active draft loads vessels and goes to vessel selection idle state`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordDraftRepository()
            val viewModel = buildViewModel(repository = repository, dispatcher = dispatcher)

            viewModel.state.test {
                assertEquals(CatchRecordFlowViewState(), awaitItem())
                viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
                assertEquals(UiStatus.Loading, awaitItem().status)
                val loaded = awaitItem()
                assertEquals(UiStatus.Idle, loaded.status)
                assertEquals(WizardStep.VesselSelection, loaded.currentStep)
                assertEquals(2, loaded.vessels.size)
                assertTrue(loaded.previouslyUsedPorts.isEmpty())
            }
        }

    @Test
    fun `enter flow with active draft goes to draft resume`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordDraftRepository()
            repository.startDraft("vessel-achilles")
            val viewModel = buildViewModel(repository = repository, dispatcher = dispatcher)

            viewModel.state.test {
                awaitItem()
                viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
                assertEquals(UiStatus.Loading, awaitItem().status)
                val loaded = awaitItem()
                assertEquals(WizardStep.DraftResume, loaded.currentStep)
                assertTrue(loaded.status is UiStatus.Content)
                assertEquals(DeparturePortEntryMode.SamePortShortcut, loaded.departurePortEntryMode)
                assertEquals("port-hastings", loaded.samePortCandidate?.id)
            }
        }

    @Test
    fun `fresh viewmodel rehydrates persisted draft after process death`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            val started = repository.startDraft("vessel-achilles").getOrThrow()
            repository.saveDraft(started.copy(isTripToday = false, departureDate = DmyDate(10, 9, 2026))).getOrThrow()

            val recreated = buildViewModel(repository = repository, dispatcher = dispatcher)
            recreated.state.test {
                awaitItem()
                recreated.dispatch(CatchRecordFlowEvent.EnterFlow)
                awaitItem()
                val loaded = awaitItem()
                assertEquals(WizardStep.DraftResume, loaded.currentStep)
                val draft = (loaded.status as UiStatus.Content<CatchRecordDraft>).value
                assertEquals(DmyDate(10, 9, 2026), draft.departureDate)
                recreated.dispatch(CatchRecordFlowEvent.ResumeDraft)
                val resumed = awaitItem()
                assertEquals(WizardStep.ReturnDate, resumed.currentStep)
            }
        }

    /**
     * Regression test: verifies both [CatchRecordDraft.departureDate] and [CatchRecordDraft.returnDate]
     * (the two values `DepartureDateScreen`/`ReturnDateScreen` seed their fields from) survive a simulated
     * process death — a brand-new [CatchRecordFlowViewModel] instance backed by the same (Room-equivalent)
     * repository must rehydrate both dates from the persisted draft, so re-entering either date step after
     * the app was closed and reopened shows the previously saved value rather than blank fields.
     */
    @Test
    fun `fresh viewmodel rehydrates both departure and return dates after process death`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            val started = repository.startDraft("vessel-achilles").getOrThrow()
            repository
                .saveDraft(
                    started.copy(
                        isTripToday = false,
                        departureDate = DmyDate(3, 7, 2026),
                        returnDate = DmyDate(5, 7, 2026),
                    ),
                ).getOrThrow()

            val recreated = buildViewModel(repository = repository, dispatcher = dispatcher)
            recreated.state.test {
                awaitItem()
                recreated.dispatch(CatchRecordFlowEvent.EnterFlow)
                awaitItem()
                val loaded = awaitItem()
                val draft = (loaded.status as UiStatus.Content<CatchRecordDraft>).value
                assertEquals(DmyDate(3, 7, 2026), draft.departureDate)
                assertEquals(DmyDate(5, 7, 2026), draft.returnDate)
            }
        }

    @Test
    fun `vessel selected persists draft and advances to trip today`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            val viewModel = buildViewModel(repository = repository, dispatcher = dispatcher)

            viewModel.state.test {
                awaitItem()
                viewModel.dispatch(CatchRecordFlowEvent.VesselSelected("vessel-hercules"))
                assertEquals(UiStatus.Loading, awaitItem().status)
                val loaded = awaitItem()
                assertEquals(WizardStep.TripToday, loaded.currentStep)
                assertEquals("vessel-hercules", (loaded.status as UiStatus.Content<CatchRecordDraft>).value.vesselId)
                assertEquals(DeparturePortEntryMode.Search, loaded.departurePortEntryMode)
            }
            assertEquals("vessel-hercules", repository.getActiveDraft("vessel-hercules").getOrThrow()?.vesselId)
        }

    @Test
    fun `trip today yes defaults both dates to injected today and skips to departure port`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            val viewModel =
                buildViewModel(repository = repository, clock = { 1_725_811_200_000L }, dispatcher = dispatcher)
            viewModel.dispatch(CatchRecordFlowEvent.VesselSelected("vessel-achilles"))
            testScheduler.advanceUntilIdle()

            viewModel.state.test {
                skipItems(1)
                viewModel.dispatch(CatchRecordFlowEvent.TripTodayAnswered(true))
                assertEquals(UiStatus.Loading, awaitItem().status)
                val updated = awaitItem()
                val draft = (updated.status as UiStatus.Content<CatchRecordDraft>).value
                assertEquals(WizardStep.DeparturePort, updated.currentStep)
                assertEquals(DmyDate(8, 9, 2024), draft.departureDate)
                assertEquals(DmyDate(8, 9, 2024), draft.returnDate)
            }
        }

    @Test
    fun `trip today no advances to departure date`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordDraftRepository()
            val viewModel = buildViewModel(repository = repository, dispatcher = dispatcher)
            viewModel.dispatch(CatchRecordFlowEvent.VesselSelected("vessel-achilles"))
            testScheduler.advanceUntilIdle()

            viewModel.state.test {
                skipItems(1)
                viewModel.dispatch(CatchRecordFlowEvent.TripTodayAnswered(false))
                assertEquals(UiStatus.Loading, awaitItem().status)
                val updated = awaitItem()
                val draft = (updated.status as UiStatus.Content<CatchRecordDraft>).value
                assertEquals(WizardStep.DepartureDate, updated.currentStep)
                assertNull(draft.departureDate)
                assertNull(draft.returnDate)
            }
        }

    @Test
    fun `same port shortcut yes sets both ports and skips to gear loop`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordDraftRepository()
            val viewModel = buildViewModel(repository = repository, dispatcher = dispatcher)
            viewModel.dispatch(CatchRecordFlowEvent.VesselSelected("vessel-achilles"))
            testScheduler.advanceUntilIdle()

            viewModel.state.test {
                skipItems(1)
                viewModel.dispatch(CatchRecordFlowEvent.SamePortShortcutAccepted)
                assertEquals(UiStatus.Loading, awaitItem().status)
                val updated = awaitItem()
                val draft = (updated.status as UiStatus.Content<CatchRecordDraft>).value
                assertEquals(WizardStep.GearLoop, updated.currentStep)
                assertEquals("port-hastings", draft.departurePort?.portId)
                assertEquals("port-hastings", draft.returnPort?.portId)
            }
        }

    @Test
    fun `same port shortcut no falls through to favourites list`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordDraftRepository()
            val viewModel = buildViewModel(repository = repository, dispatcher = dispatcher)
            viewModel.dispatch(CatchRecordFlowEvent.VesselSelected("vessel-achilles"))
            testScheduler.advanceUntilIdle()

            viewModel.state.test {
                skipItems(1)
                viewModel.dispatch(CatchRecordFlowEvent.SamePortShortcutDeclined)
                val updated = awaitItem()
                assertEquals(DeparturePortEntryMode.FavouriteList, updated.departurePortEntryMode)
            }
        }

    @Test
    fun `delete draft returns to vessel selection and removes persisted draft`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            val viewModel = buildViewModel(repository = repository, dispatcher = dispatcher)
            repository.startDraft("vessel-achilles")
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
            testScheduler.advanceUntilIdle()

            viewModel.state.test {
                skipItems(1)
                viewModel.dispatch(CatchRecordFlowEvent.DeleteDraft)
                assertEquals(UiStatus.Loading, awaitItem().status)
                val updated = awaitItem()
                assertEquals(UiStatus.Idle, updated.status)
                assertEquals(WizardStep.VesselSelection, updated.currentStep)
            }
            assertTrue(repository.deletedDraftIds.contains("draft-1"))
        }

    @Test
    fun `repository failure surfaces retryable error state`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordDraftRepository().apply { failNextOperation = true }
            val viewModel = buildViewModel(repository = repository, dispatcher = dispatcher)

            viewModel.state.test {
                awaitItem()
                viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
                assertEquals(UiStatus.Loading, awaitItem().status)
                val errored = awaitItem()
                assertTrue(errored.status is UiStatus.Error)
                assertTrue((errored.status as UiStatus.Error).isRetryable)
            }
        }
}
