@file:Suppress("detekt.MaxLineLength", "detekt.LargeClass")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow

import app.cash.turbine.test
import kotlinx.coroutines.CancellationException
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
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlowForDraft("draft-1"))
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
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlowForDraft("draft-1"))
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
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlowForDraft("draft-1"))
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
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlowForDraft("draft-1"))
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
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlowForDraft("draft-1"))
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
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlowForDraft("draft-1"))
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
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlowForDraft("draft-1"))
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
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlowForDraft("draft-1"))
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

    /** Regression: Retry used to silently no-op here because [CatchRecordFlowEvent.MarkReadyToSubmit]'s
     *  handler is `currentDraftOrNull()`-guarded, and an [UiStatus.Error] has no draft to read. */
    @Test
    fun `retry now resumes mark ready to submit instead of silently no-opping`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            val started = repository.startDraft("vessel-achilles").getOrThrow()
            repository.saveDraft(started.asSubmissionReady()).getOrThrow()
            val viewModel = buildViewModel(repository = repository, dispatcher = dispatcher)
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlowForDraft("draft-1"))
            testScheduler.advanceUntilIdle()
            viewModel.dispatch(CatchRecordFlowEvent.ResumeDraft)
            testScheduler.advanceUntilIdle()

            repository.failNextOperation = true
            viewModel.dispatch(CatchRecordFlowEvent.MarkReadyToSubmit)
            testScheduler.advanceUntilIdle()
            assertTrue(viewModel.state.value.status is UiStatus.Error)

            viewModel.dispatch(CatchRecordFlowEvent.Retry)
            testScheduler.advanceUntilIdle()

            val updated = (viewModel.state.value.status as UiStatus.Content<CatchRecordDraft>).value
            assertEquals(DraftStatus.ReadyToSubmit, updated.status)
        }

    // --- accept declaration and submit: validation gate + downstream persistence failures --------

    @Test
    fun `accept declaration and submit blocks an incomplete draft with a terminal non-retryable error`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            repository.startDraft("vessel-achilles")
            val viewModel = buildViewModel(repository = repository, dispatcher = dispatcher)
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlowForDraft("draft-1"))
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
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlowForDraft("draft-1"))
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
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlowForDraft("draft-1"))
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
    fun `accept declaration and submit local save failure shows the on-device save message`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            val started = repository.startDraft("vessel-achilles").getOrThrow()
            repository.saveDraft(started.asSubmissionReady()).getOrThrow()
            val viewModel =
                buildViewModel(
                    repository = repository,
                    connectivityChecker = FakeNetworkConnectivityChecker(connected = false),
                    dispatcher = dispatcher,
                )
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlowForDraft("draft-1"))
            testScheduler.advanceUntilIdle()
            viewModel.dispatch(CatchRecordFlowEvent.ResumeDraft)
            testScheduler.advanceUntilIdle()

            repository.failNextOperation = true
            viewModel.dispatch(CatchRecordFlowEvent.AcceptDeclarationAndSubmit)
            testScheduler.advanceUntilIdle()

            val errored = viewModel.state.value.status as UiStatus.Error
            assertEquals("We could not save your catch record on this device. Try again.", errored.message)
        }

    /** Another event's failure must keep the original, non-submit-specific message (see
     *  [SafeErrorMapper]'s `map` vs `mapSubmitLocalSave`). */
    @Test
    fun `delete draft failure keeps the original connection message, not the submit-specific one`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            repository.startDraft("vessel-achilles")
            val viewModel = buildViewModel(repository = repository, dispatcher = dispatcher)
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlowForDraft("draft-1"))
            testScheduler.advanceUntilIdle()

            repository.failNextOperation = true
            viewModel.dispatch(CatchRecordFlowEvent.DeleteDraft)
            testScheduler.advanceUntilIdle()

            val errored = viewModel.state.value.status as UiStatus.Error
            assertEquals("We could not save your changes. Check your connection and try again.", errored.message)
        }

    /** Regression: offline submit, local save fails once then succeeds on Retry — the record must end up
     *  queued for sync, not stuck on a dead "Try again" (see [retryLastFailedEvent]'s doc comment). */
    @Test
    fun `retry after an offline submit local-save failure enqueues pending sync exactly once`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            val started = repository.startDraft("vessel-achilles").getOrThrow()
            repository.saveDraft(started.asSubmissionReady()).getOrThrow()
            val syncScheduler = FakeCatchRecordSyncScheduler()
            val viewModel =
                buildViewModel(
                    repository = repository,
                    connectivityChecker = FakeNetworkConnectivityChecker(connected = false),
                    syncScheduler = syncScheduler,
                    dispatcher = dispatcher,
                )
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlowForDraft("draft-1"))
            testScheduler.advanceUntilIdle()
            viewModel.dispatch(CatchRecordFlowEvent.ResumeDraft)
            testScheduler.advanceUntilIdle()

            repository.failNextOperation = true
            viewModel.dispatch(CatchRecordFlowEvent.AcceptDeclarationAndSubmit)
            testScheduler.advanceUntilIdle()
            assertTrue(viewModel.state.value.status is UiStatus.Error)
            assertTrue(syncScheduler.scheduledDraftIds.isEmpty())

            viewModel.dispatch(CatchRecordFlowEvent.Retry)
            testScheduler.advanceUntilIdle()

            val resumed = viewModel.state.value
            assertEquals(WizardStep.SubmissionPendingSync, resumed.currentStep)
            assertFalse(resumed.isSubmitting)
            val draft = (resumed.status as UiStatus.Content<CatchRecordDraft>).value
            assertEquals(DraftStatus.PendingSync, draft.status)
            assertEquals(listOf(started.id), syncScheduler.scheduledDraftIds)
        }

    /** Retry itself can fail again (e.g. storage still full); a third attempt then succeeds. */
    @Test
    fun `retry after a repeated offline submit local-save failure succeeds on the third attempt`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            val started = repository.startDraft("vessel-achilles").getOrThrow()
            repository.saveDraft(started.asSubmissionReady()).getOrThrow()
            val syncScheduler = FakeCatchRecordSyncScheduler()
            val viewModel =
                buildViewModel(
                    repository = repository,
                    connectivityChecker = FakeNetworkConnectivityChecker(connected = false),
                    syncScheduler = syncScheduler,
                    dispatcher = dispatcher,
                )
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlowForDraft("draft-1"))
            testScheduler.advanceUntilIdle()
            viewModel.dispatch(CatchRecordFlowEvent.ResumeDraft)
            testScheduler.advanceUntilIdle()

            repository.failNextOperation = true
            viewModel.dispatch(CatchRecordFlowEvent.AcceptDeclarationAndSubmit)
            testScheduler.advanceUntilIdle()
            assertTrue((viewModel.state.value.status as UiStatus.Error).isRetryable)

            repository.failNextOperation = true
            viewModel.dispatch(CatchRecordFlowEvent.Retry)
            testScheduler.advanceUntilIdle()
            assertTrue((viewModel.state.value.status as UiStatus.Error).isRetryable)
            assertTrue(syncScheduler.scheduledDraftIds.isEmpty())

            viewModel.dispatch(CatchRecordFlowEvent.Retry)
            testScheduler.advanceUntilIdle()

            val resumed = viewModel.state.value
            assertEquals(WizardStep.SubmissionPendingSync, resumed.currentStep)
            val draft = (resumed.status as UiStatus.Content<CatchRecordDraft>).value
            assertEquals(DraftStatus.PendingSync, draft.status)
            assertEquals(listOf(started.id), syncScheduler.scheduledDraftIds)
        }

    /** Regression guard: retrying a failure with no prior draft (e.g. the very first vessel-selection
     *  attempt) must still redispatch normally, not get stuck behind a draft-restoration step. */
    @Test
    fun `retry with no known draft still redispatches and can succeed`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordDraftRepository()
            val viewModel = buildViewModel(repository = repository, dispatcher = dispatcher)

            repository.failNextOperation = true
            viewModel.dispatch(CatchRecordFlowEvent.VesselSelected("vessel-hercules"))
            testScheduler.advanceUntilIdle()
            assertTrue(viewModel.state.value.status is UiStatus.Error)

            viewModel.dispatch(CatchRecordFlowEvent.Retry)
            testScheduler.advanceUntilIdle()

            val content = viewModel.state.value.status as UiStatus.Content<CatchRecordDraft>
            assertEquals("vessel-hercules", content.value.vesselId)
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
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlowForDraft("draft-1"))
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
                viewModel.dispatch(CatchRecordFlowEvent.EnterFlowForDraft("draft-1"))
                var state = awaitItem()
                while (state.status !is UiStatus.Content<*>) state = awaitItem()
                assertEquals("draft-1", (state.status as UiStatus.Content<CatchRecordDraft>).value.id)
            }
        }

    // --- duplicate Retry while a submission stage is already in flight ----------------------------

    /**
     * Regression guard: a stale Retry for a *previous* draft's failed submission must not resume once a
     * *different* draft's own Accept-and-submit is already in flight — otherwise the stale retry would
     * silently resubmit the earlier draft (see [retrySubmissionStage]'s `isSubmitting` guard). This is the
     * only way to observe that guard: the ordinary "tap Retry twice" case never reaches it, since
     * `retryLastFailedEvent` clears `lastFailedAction` before the first retry even schedules its coroutine.
     */
    @Test
    fun `a stale retry for an earlier draft is ignored while a different draft is submitting`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            var draftCounter = 0
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-${++draftCounter}" })
            val draftA = repository.startDraft("vessel-achilles").getOrThrow()
            repository.saveDraft(draftA.asSubmissionReady()).getOrThrow()
            val draftB = repository.startDraft("vessel-hercules").getOrThrow()
            repository.saveDraft(draftB.asSubmissionReady()).getOrThrow()
            val syncScheduler = FakeCatchRecordSyncScheduler()
            val viewModel =
                buildViewModel(
                    repository = repository,
                    connectivityChecker = FakeNetworkConnectivityChecker(connected = false),
                    syncScheduler = syncScheduler,
                    dispatcher = dispatcher,
                )

            viewModel.dispatch(CatchRecordFlowEvent.EnterFlowForDraft(draftA.id))
            testScheduler.advanceUntilIdle()
            viewModel.dispatch(CatchRecordFlowEvent.ResumeDraft)
            testScheduler.advanceUntilIdle()
            repository.failNextOperation = true
            viewModel.dispatch(CatchRecordFlowEvent.AcceptDeclarationAndSubmit)
            testScheduler.advanceUntilIdle()
            assertTrue((viewModel.state.value.status as UiStatus.Error).isRetryable)

            viewModel.dispatch(CatchRecordFlowEvent.EnterFlowForDraft(draftB.id))
            testScheduler.advanceUntilIdle()
            viewModel.dispatch(CatchRecordFlowEvent.ResumeDraft)
            testScheduler.advanceUntilIdle()

            viewModel.dispatch(CatchRecordFlowEvent.AcceptDeclarationAndSubmit)
            viewModel.dispatch(CatchRecordFlowEvent.Retry)
            testScheduler.advanceUntilIdle()

            assertEquals(WizardStep.SubmissionPendingSync, viewModel.state.value.currentStep)
            assertEquals(listOf(draftB.id), syncScheduler.scheduledDraftIds)
        }

    // --- a cancellation during a submission stage is rethrown, never mapped to an error -----------

    /** [CatchRecordFlowViewModel.runSubmissionStage] must rethrow a [CancellationException] rather than
     *  mapping it to a user-facing error — mapping it would surface a confusing message for ordinary
     *  structured-concurrency cancellation (e.g. the screen leaving composition). */
    @Test
    fun `a cancellation thrown mid-submission is rethrown and never surfaced as an error`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            val started = repository.startDraft("vessel-achilles").getOrThrow()
            repository.saveDraft(started.asSubmissionReady()).getOrThrow()
            val connectivityChecker = FakeNetworkConnectivityChecker(connected = true)
            val viewModel =
                buildViewModel(
                    repository = repository,
                    connectivityChecker = connectivityChecker,
                    dispatcher = dispatcher,
                )
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlowForDraft("draft-1"))
            testScheduler.advanceUntilIdle()
            viewModel.dispatch(CatchRecordFlowEvent.ResumeDraft)
            testScheduler.advanceUntilIdle()
            connectivityChecker.throwOnNextIsConnected = CancellationException("Simulated cancellation")

            viewModel.dispatch(CatchRecordFlowEvent.AcceptDeclarationAndSubmit)
            testScheduler.advanceUntilIdle()

            val state = viewModel.state.value
            assertTrue(state.status is UiStatus.Loading)
            assertTrue(state.isSubmitting)
        }

    // --- retryLastFailedEvent is a no-op when nothing has previously failed ------------------------

    /** [CatchRecordFlowViewModel.retryLastFailedEvent]'s `?: return` guard: Retry before any failure
     *  (e.g. a stray dispatch) must not throw or change state. */
    @Test
    fun `retry with no prior failure at all is a no-op`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordDraftRepository()
            val viewModel = buildViewModel(repository = repository, dispatcher = dispatcher)

            viewModel.dispatch(CatchRecordFlowEvent.Retry)
            testScheduler.advanceUntilIdle()

            assertEquals(UiStatus.Idle, viewModel.state.value.status)
        }
}
