@file:Suppress("detekt.MaxLineLength", "detekt.LargeClass")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DmyDate
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelection
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelectionMode
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.SpeciesWeightEntry

/** Failure-path and rarely-dispatched-event tests, split from [CatchRecordFlowViewModelTests] for size. */
@OptIn(ExperimentalCoroutinesApi::class)
class CatchRecordFlowViewModelFailurePathsTests {
    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Suppress("LongParameterList")
    private fun buildViewModel(
        repository: FakeCatchRecordDraftRepository,
        referenceDataRepository: FakeReferenceDataRepository = FakeReferenceDataRepository(),
        submissionRepository: FakeCatchRecordSubmissionRepository = FakeCatchRecordSubmissionRepository(),
        connectivityChecker: FakeNetworkConnectivityChecker = FakeNetworkConnectivityChecker(),
        syncScheduler: FakeCatchRecordSyncScheduler = FakeCatchRecordSyncScheduler(),
        clock: () -> Long = { 0L },
        idFactory: () -> String = { "generated-id" },
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
    ) = CatchRecordFlowViewModel(
        repository,
        referenceDataRepository,
        submissionRepository,
        connectivityChecker,
        syncScheduler,
        clock,
        idFactory,
        dispatcher,
    )

    private fun confirmedGearUse(
        id: String = "gear-use-1",
        speciesWeights: List<SpeciesWeightEntry> = emptyList(),
    ) = GearUse(
        id = id,
        gearTypeId = "gear-seine-nets",
        statisticalSubRectangleCode = "38E95",
        confirmedUsedOnTrip = true,
        speciesWeights = speciesWeights,
    )

    private fun CatchRecordDraft.asSubmissionReady(): CatchRecordDraft =
        copy(
            isTripToday = true,
            departureDate = DmyDate(1, 1, 2020),
            returnDate = DmyDate(1, 1, 2020),
            departurePort = PortSelection("port-hastings", PortSelectionMode.Favourite),
            returnPort = PortSelection("port-hastings", PortSelectionMode.Favourite),
            gearUses =
                listOf(
                    confirmedGearUse(
                        speciesWeights =
                            listOf(
                                SpeciesWeightEntry(id = "species-1", speciesId = "species-cod", confirmedCaught = true),
                            ),
                    ),
                ),
            notLandedStraightAway = false,
        )

    // --- catchRecordReference extension property ----------------------------------------------

    @Test
    fun `catchRecordReference returns the persisted reference only for content status`() {
        val draft =
            CatchRecordDraft(id = "d1", vesselId = "v1", modifiedAtEpochMillis = 0L, catchRecordReference = "REF-1")
        val contentState = CatchRecordFlowViewState(status = UiStatus.Content(draft))
        val idleState = CatchRecordFlowViewState(status = UiStatus.Idle)

        assertEquals("REF-1", contentState.catchRecordReference)
        assertNull(idleState.catchRecordReference)
    }

    // --- delete draft failure -------------------------------------------------------------------

    @Test
    fun `delete draft surfaces a retryable error when the repository fails`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            repository.startDraft("vessel-achilles")
            val viewModel = buildViewModel(repository = repository, dispatcher = dispatcher)
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
            testScheduler.advanceUntilIdle()

            repository.failNextOperation = true
            viewModel.state.test {
                skipItems(1)
                viewModel.dispatch(CatchRecordFlowEvent.DeleteDraft)
                assertEquals(UiStatus.Loading, awaitItem().status)
                val errored = awaitItem()
                assertTrue(errored.status is UiStatus.Error)
                assertTrue((errored.status as UiStatus.Error).isRetryable)
            }
        }

    // --- vessel selection failing to start a brand new draft ----------------------------------------

    @Test
    fun `vessel selected surfaces a retryable error when starting a brand new draft fails`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordDraftRepository()
            val viewModel = buildViewModel(repository = repository, dispatcher = dispatcher)

            repository.failNextOperation = true
            viewModel.state.test {
                skipItems(1)
                viewModel.dispatch(CatchRecordFlowEvent.VesselSelected("vessel-hercules"))
                assertEquals(UiStatus.Loading, awaitItem().status)
                val errored = awaitItem()
                assertTrue(errored.status is UiStatus.Error)
                assertTrue((errored.status as UiStatus.Error).isRetryable)
            }
            assertFalse(viewModel.state.value.isDraftPersisted)
        }

    // --- species added to the current pending gear (SpeciesAddedToCurrentGear) -------------------

    @Test
    fun `species added to current gear appends a new entry then a repeat add is a no-op`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            val started = repository.startDraft("vessel-achilles").getOrThrow()
            repository.saveDraft(started.copy(gearUses = listOf(confirmedGearUse()))).getOrThrow()
            val viewModel = buildViewModel(repository = repository, dispatcher = dispatcher)
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
            testScheduler.advanceUntilIdle()
            viewModel.dispatch(CatchRecordFlowEvent.ResumeDraft)
            testScheduler.advanceUntilIdle()

            viewModel.dispatch(CatchRecordFlowEvent.SpeciesAddedToCurrentGear("species-cod"))
            testScheduler.advanceUntilIdle()
            val afterFirstAdd = (viewModel.state.value.status as UiStatus.Content<CatchRecordDraft>).value
            assertEquals(
                1,
                afterFirstAdd.gearUses
                    .single()
                    .speciesWeights.size,
            )
            assertEquals(WizardStep.GearSpeciesChecklist, viewModel.state.value.currentStep)

            viewModel.dispatch(CatchRecordFlowEvent.SpeciesAddedToCurrentGear("species-cod"))
            testScheduler.advanceUntilIdle()
            val afterSecondAdd = (viewModel.state.value.status as UiStatus.Content<CatchRecordDraft>).value
            assertEquals(
                "re-adding the same species to the same gear must not create a duplicate entry",
                1,
                afterSecondAdd.gearUses
                    .single()
                    .speciesWeights.size,
            )
        }

    // --- persisting gear/species removal without a step change (persistDraftWithoutStepChange) ---

    @Test
    fun `gear removed surfaces a retryable error when persisting the removal fails`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            val started = repository.startDraft("vessel-achilles").getOrThrow()
            repository.saveDraft(started.copy(gearUses = listOf(confirmedGearUse()))).getOrThrow()
            val viewModel = buildViewModel(repository = repository, dispatcher = dispatcher)
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
            testScheduler.advanceUntilIdle()
            viewModel.dispatch(CatchRecordFlowEvent.ResumeDraft)
            testScheduler.advanceUntilIdle()
            val currentDraft = (viewModel.state.value.status as UiStatus.Content<CatchRecordDraft>).value

            repository.failNextOperation = true
            viewModel.state.test {
                skipItems(1)
                viewModel.dispatch(CatchRecordFlowEvent.GearRemoved(currentDraft.copy(gearUses = emptyList())))
                assertEquals(UiStatus.Loading, awaitItem().status)
                val errored = awaitItem()
                assertTrue(errored.status is UiStatus.Error)
            }
        }

    // --- persisting a Check-your-answers "Change" gear edit (persistGearEdit) --------------------

    @Test
    fun `editing gear species weights persists the replacement list and returns to check your answers`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            val started = repository.startDraft("vessel-achilles").getOrThrow()
            repository.saveDraft(started.copy(gearUses = listOf(confirmedGearUse()))).getOrThrow()
            val viewModel = buildViewModel(repository = repository, dispatcher = dispatcher)
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
            testScheduler.advanceUntilIdle()
            viewModel.dispatch(CatchRecordFlowEvent.ResumeDraft)
            testScheduler.advanceUntilIdle()

            val newWeights = listOf(SpeciesWeightEntry(id = "sw-1", speciesId = "species-cod", confirmedCaught = true))
            viewModel.dispatch(CatchRecordFlowEvent.EditGearSpeciesWeights("gear-use-1", newWeights))
            testScheduler.advanceUntilIdle()

            val updated = (viewModel.state.value.status as UiStatus.Content<CatchRecordDraft>).value
            assertEquals(newWeights, updated.gearUses.single().speciesWeights)
            assertEquals(WizardStep.CheckYourAnswers, viewModel.state.value.currentStep)
        }

    @Test
    fun `editing gear stat rectangle surfaces a retryable error when persisting fails`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            val started = repository.startDraft("vessel-achilles").getOrThrow()
            repository.saveDraft(started.copy(gearUses = listOf(confirmedGearUse()))).getOrThrow()
            val viewModel = buildViewModel(repository = repository, dispatcher = dispatcher)
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
            testScheduler.advanceUntilIdle()
            viewModel.dispatch(CatchRecordFlowEvent.ResumeDraft)
            testScheduler.advanceUntilIdle()

            repository.failNextOperation = true
            viewModel.state.test {
                skipItems(1)
                viewModel.dispatch(CatchRecordFlowEvent.EditGearStatRectangle("gear-use-1", "38F02"))
                assertEquals(UiStatus.Loading, awaitItem().status)
                val errored = awaitItem()
                assertTrue(errored.status is UiStatus.Error)
            }
        }

    // --- species added to an explicit, already-confirmed gear use (SpeciesAddedToGearUse) --------

    @Test
    fun `species added to gear use adds a new entry while a repeat is a no-op re-navigation`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            val started = repository.startDraft("vessel-achilles").getOrThrow()
            val existingSpecies = SpeciesWeightEntry(id = "sw-1", speciesId = "species-cod", confirmedCaught = true)
            repository
                .saveDraft(started.copy(gearUses = listOf(confirmedGearUse(speciesWeights = listOf(existingSpecies)))))
                .getOrThrow()
            val viewModel = buildViewModel(repository = repository, dispatcher = dispatcher)
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
            testScheduler.advanceUntilIdle()
            viewModel.dispatch(CatchRecordFlowEvent.ResumeDraft)
            testScheduler.advanceUntilIdle()

            // Already-added species: a no-op re-navigation only, no new entry/write.
            viewModel.dispatch(CatchRecordFlowEvent.SpeciesAddedToGearUse("gear-use-1", "species-cod"))
            testScheduler.advanceUntilIdle()
            val afterNoOp = (viewModel.state.value.status as UiStatus.Content<CatchRecordDraft>).value
            assertEquals(
                1,
                afterNoOp.gearUses
                    .single()
                    .speciesWeights.size,
            )
            assertEquals(WizardStep.GearSpeciesChecklist, viewModel.state.value.currentStep)

            // A genuinely new species: persisted via persistGearEdit.
            viewModel.dispatch(CatchRecordFlowEvent.SpeciesAddedToGearUse("gear-use-1", "species-plaice"))
            testScheduler.advanceUntilIdle()
            val afterAdd = (viewModel.state.value.status as UiStatus.Content<CatchRecordDraft>).value
            assertEquals(
                2,
                afterAdd.gearUses
                    .single()
                    .speciesWeights.size,
            )
        }

    // --- mark ready to submit --------------------------------------------------------------------

    @Test
    fun `mark ready to submit transitions the draft to ready to submit`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            val started = repository.startDraft("vessel-achilles").getOrThrow()
            repository.saveDraft(started.asSubmissionReady()).getOrThrow()
            val viewModel = buildViewModel(repository = repository, dispatcher = dispatcher)
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
            testScheduler.advanceUntilIdle()
            viewModel.dispatch(CatchRecordFlowEvent.ResumeDraft)
            testScheduler.advanceUntilIdle()

            viewModel.dispatch(CatchRecordFlowEvent.MarkReadyToSubmit)
            testScheduler.advanceUntilIdle()

            val updated = (viewModel.state.value.status as UiStatus.Content<CatchRecordDraft>).value
            assertEquals(DraftStatus.ReadyToSubmit, updated.status)
        }

    @Test
    fun `mark ready to submit surfaces a retryable error when it fails`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            val started = repository.startDraft("vessel-achilles").getOrThrow()
            repository.saveDraft(started.asSubmissionReady()).getOrThrow()
            val viewModel = buildViewModel(repository = repository, dispatcher = dispatcher)
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
            testScheduler.advanceUntilIdle()
            viewModel.dispatch(CatchRecordFlowEvent.ResumeDraft)
            testScheduler.advanceUntilIdle()

            repository.failNextOperation = true
            viewModel.state.test {
                skipItems(1)
                viewModel.dispatch(CatchRecordFlowEvent.MarkReadyToSubmit)
                val errored = awaitItem()
                assertTrue(errored.status is UiStatus.Error)
            }
        }

    // --- accept declaration and submit: validation gate + downstream persistence failures --------

    @Test
    fun `accept declaration and submit blocks an incomplete draft with a terminal non-retryable error`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            repository.startDraft("vessel-achilles")
            val viewModel = buildViewModel(repository = repository, dispatcher = dispatcher)
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
            testScheduler.advanceUntilIdle()
            viewModel.dispatch(CatchRecordFlowEvent.ResumeDraft)
            testScheduler.advanceUntilIdle()

            viewModel.dispatch(CatchRecordFlowEvent.AcceptDeclarationAndSubmit)
            testScheduler.advanceUntilIdle()

            val status = viewModel.state.value.status
            assertTrue(status is UiStatus.Error)
            assertFalse((status as UiStatus.Error).isRetryable)
        }

    @Test
    fun `accept declaration and submit surfaces a retryable error when online persistence fails`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            val started = repository.startDraft("vessel-achilles").getOrThrow()
            repository.saveDraft(started.asSubmissionReady()).getOrThrow()
            val viewModel =
                buildViewModel(
                    repository = repository,
                    submissionRepository = FakeCatchRecordSubmissionRepository(shouldSucceed = true),
                    connectivityChecker = FakeNetworkConnectivityChecker(connected = true),
                    dispatcher = dispatcher,
                )
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
            testScheduler.advanceUntilIdle()
            viewModel.dispatch(CatchRecordFlowEvent.ResumeDraft)
            testScheduler.advanceUntilIdle()

            repository.failNextOperation = true
            viewModel.state.test {
                skipItems(1)
                viewModel.dispatch(CatchRecordFlowEvent.AcceptDeclarationAndSubmit)
                assertEquals(UiStatus.Loading, awaitItem().status)
                val errored = awaitItem()
                assertTrue(errored.status is UiStatus.Error)
                assertTrue((errored.status as UiStatus.Error).isRetryable)
            }
        }

    @Test
    fun `accept declaration and submit surfaces a retryable error when enqueueing pending sync fails to persist`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            val started = repository.startDraft("vessel-achilles").getOrThrow()
            repository.saveDraft(started.asSubmissionReady()).getOrThrow()
            val viewModel =
                buildViewModel(
                    repository = repository,
                    submissionRepository = FakeCatchRecordSubmissionRepository(shouldSucceed = true),
                    connectivityChecker = FakeNetworkConnectivityChecker(connected = false),
                    dispatcher = dispatcher,
                )
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
            testScheduler.advanceUntilIdle()
            viewModel.dispatch(CatchRecordFlowEvent.ResumeDraft)
            testScheduler.advanceUntilIdle()

            repository.failNextOperation = true
            viewModel.state.test {
                skipItems(1)
                viewModel.dispatch(CatchRecordFlowEvent.AcceptDeclarationAndSubmit)
                assertEquals(UiStatus.Loading, awaitItem().status)
                val errored = awaitItem()
                assertTrue(errored.status is UiStatus.Error)
                assertTrue((errored.status as UiStatus.Error).isRetryable)
            }
        }

    // --- species removed persists the filtered checklist without changing step -------------------

    @Test
    fun `species removed persists the filtered species list without changing step`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            val keptSpecies = SpeciesWeightEntry(id = "sw-keep", speciesId = "species-cod", confirmedCaught = true)
            val removedSpecies =
                SpeciesWeightEntry(id = "sw-remove", speciesId = "species-plaice", confirmedCaught = true)
            val started = repository.startDraft("vessel-achilles").getOrThrow()
            repository
                .saveDraft(
                    started.copy(
                        gearUses = listOf(confirmedGearUse(speciesWeights = listOf(keptSpecies, removedSpecies))),
                    ),
                ).getOrThrow()
            val viewModel = buildViewModel(repository = repository, dispatcher = dispatcher)
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
            testScheduler.advanceUntilIdle()
            viewModel.dispatch(CatchRecordFlowEvent.ResumeDraft)
            testScheduler.advanceUntilIdle()
            val currentDraft = (viewModel.state.value.status as UiStatus.Content<CatchRecordDraft>).value
            val stepBeforeRemoval = viewModel.state.value.currentStep

            viewModel.state.test {
                skipItems(1)
                val updatedDraft =
                    currentDraft.copy(gearUses = listOf(confirmedGearUse(speciesWeights = listOf(keptSpecies))))
                viewModel.dispatch(CatchRecordFlowEvent.SpeciesRemoved(updatedDraft))
                assertEquals(UiStatus.Loading, awaitItem().status)
                val afterRemoval = awaitItem()
                assertEquals(stepBeforeRemoval, afterRemoval.currentStep)
                val draft = (afterRemoval.status as UiStatus.Content<CatchRecordDraft>).value
                assertEquals(
                    listOf("sw-keep"),
                    draft.gearUses
                        .single()
                        .speciesWeights
                        .map { it.id },
                )
            }
        }

    // --- constructor default dispatcher -------------------------------------------------------------

    @Test
    fun `omitting the dispatcher argument still loads the draft when entering the flow`() =
        runTest {
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            repository.startDraft("vessel-achilles")
            val viewModel =
                CatchRecordFlowViewModel(
                    repository,
                    FakeReferenceDataRepository(),
                    FakeCatchRecordSubmissionRepository(),
                    FakeNetworkConnectivityChecker(),
                    FakeCatchRecordSyncScheduler(),
                    { 0L },
                    { "generated-id" },
                )

            viewModel.state.test {
                assertEquals(UiStatus.Idle, awaitItem().status)
                viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
                var state = awaitItem()
                while (state.status !is UiStatus.Content<*>) state = awaitItem()
                assertEquals("draft-1", (state.status as UiStatus.Content<CatchRecordDraft>).value.id)
            }
        }
}
