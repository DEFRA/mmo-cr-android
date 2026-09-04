package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation

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
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.FakeCatchRecordRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.CatchRecord
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.GetCatchRecordsUseCase
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.SaveCatchRecordUseCase

@OptIn(ExperimentalCoroutinesApi::class)
class CatchRecordViewModelTests {
    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
        repository: FakeCatchRecordRepository = FakeCatchRecordRepository(),
        idFactory: () -> String = { "generated-id" },
    ) = CatchRecordViewModel(
        saveCatchRecordUseCase = SaveCatchRecordUseCase(repository, clock = { 0L }),
        getCatchRecordsUseCase = GetCatchRecordsUseCase(repository),
        idFactory = idFactory,
        defaultDispatcher = dispatcher,
    )

    @Test
    fun `starts empty then saving a valid catch adds it to the list`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val viewModel = buildViewModel(dispatcher)

            viewModel.state.test {
                assertEquals(UiStatus.Loading, awaitItem().status)
                val initial = awaitItem().status
                assertTrue(initial is UiStatus.Content)
                assertTrue((initial as UiStatus.Content<List<CatchRecord>>).value.isEmpty())

                viewModel.dispatch(CatchRecordEvent.SpeciesChanged("Cod"))
                assertEquals("Cod", awaitItem().species)
                viewModel.dispatch(CatchRecordEvent.WeightChanged("2.5"))
                assertEquals("2.5", awaitItem().weightKgInput)

                viewModel.dispatch(CatchRecordEvent.SaveRequested)

                assertEquals(UiStatus.Loading, awaitItem().status)
                val afterSave = awaitItem().status
                assertTrue(afterSave is UiStatus.Content)
                val records = (afterSave as UiStatus.Content<List<CatchRecord>>).value
                assertEquals(1, records.size)
                assertEquals("Cod", records.first().species)
            }
        }

    @Test
    fun `saving with a non-numeric weight surfaces a validation message and does not persist`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordRepository()
            val viewModel = buildViewModel(dispatcher, repository = repository)

            viewModel.state.test {
                assertEquals(UiStatus.Loading, awaitItem().status)
                assertTrue(awaitItem().status is UiStatus.Content)

                viewModel.dispatch(CatchRecordEvent.SpeciesChanged("Cod"))
                assertEquals("Cod", awaitItem().species)
                viewModel.dispatch(CatchRecordEvent.WeightChanged("not-a-number"))
                assertEquals("not-a-number", awaitItem().weightKgInput)

                viewModel.dispatch(CatchRecordEvent.SaveRequested)

                assertEquals(
                    "Weight must be a number greater than zero",
                    awaitItem().validationMessage,
                )
            }
            assertTrue(repository.getCatchRecords().getOrThrow().isEmpty())
        }
}
