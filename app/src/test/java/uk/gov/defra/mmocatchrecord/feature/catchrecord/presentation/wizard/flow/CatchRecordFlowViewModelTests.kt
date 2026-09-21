@file:Suppress("detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.MeasurementValue
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelection
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelectionMode
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.SpeciesWeightEntry
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldKeys

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

    @Suppress("LongParameterList")
    private fun buildViewModel(
        repository: FakeCatchRecordDraftRepository,
        referenceDataRepository: FakeReferenceDataRepository = FakeReferenceDataRepository(),
        submissionRepository: FakeCatchRecordSubmissionRepository = FakeCatchRecordSubmissionRepository(),
        connectivityChecker: FakeNetworkConnectivityChecker = FakeNetworkConnectivityChecker(),
        syncScheduler: FakeCatchRecordSyncScheduler = FakeCatchRecordSyncScheduler(),
        clock: () -> Long = { 0L },
        idFactory: () -> String = gearIdSequenceFactory(),
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

    /** Deterministic, unique-per-call id generator for gear uses created during a single test. */
    private fun gearIdSequenceFactory(): () -> String {
        var counter = 0
        return { "gear-use-${counter++}" }
    }

    /**
     * A fully complete, [CatchRecordDraftValidation]-valid draft (trip timing, ports, one confirmed gear
     * use with a stat rectangle and a confirmed species, and the not-landed decision answered `false`) —
     * the minimum a draft must satisfy to reach [WizardStep.CheckYourAnswers]/be submitted. Used by the
     * Phase 8 accept-declaration-and-submit tests below, which exercise submission itself rather than the
     * per-step wizard gating already covered elsewhere.
     */
    private fun CatchRecordDraft.asSubmissionReady(): CatchRecordDraft =
        copy(
            isTripToday = true,
            departureDate = DmyDate(1, 1, 2020),
            returnDate = DmyDate(1, 1, 2020),
            departurePort = PortSelection("port-hastings", PortSelectionMode.Favourite),
            returnPort = PortSelection("port-hastings", PortSelectionMode.Favourite),
            gearUses =
                listOf(
                    GearUse(
                        id = "gear-use-1",
                        gearTypeId = "gear-seine-nets",
                        statisticalSubRectangleCode = "38E95",
                        confirmedUsedOnTrip = true,
                        speciesWeights = listOf(SpeciesWeightEntry(id = "species-1", speciesId = "species-cod", confirmedCaught = true)),
                    ),
                ),
            notLandedStraightAway = false,
        )

    /**
     * Shared setup for the Phase 4 per-gear stat-rectangle loop test: a resumed draft with two confirmed
     * (but no stat-rectangle yet) gear uses, sat on [WizardStep.GearStatRectangle] awaiting the first gear's
     * rectangle. Extracted purely to keep the test body under detekt's [LongMethod] limit.
     */
    private suspend fun TestScope.setUpTwoConfirmedGearsAwaitingStatRectangle():
        Triple<CatchRecordFlowViewModel, GearUse, GearUse> {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
        val started = repository.startDraft("vessel-achilles").getOrThrow()
        val gearOne =
            GearUse(
                id = "gear-use-1",
                gearTypeId = "gear-seine-nets",
                statisticalSubRectangleCode = null,
                confirmedUsedOnTrip = true,
            )
        val gearTwo =
            GearUse(
                id = "gear-use-2",
                gearTypeId = "gear-seine-nets",
                statisticalSubRectangleCode = null,
                confirmedUsedOnTrip = true,
            )
        repository
            .saveDraft(
                started.copy(
                    isTripToday = true,
                    departurePort = PortSelection("port-hastings", PortSelectionMode.Favourite),
                    returnPort = PortSelection("port-hastings", PortSelectionMode.Favourite),
                    gearUses = listOf(gearOne, gearTwo),
                ),
            ).getOrThrow()

        val viewModel = buildViewModel(repository = repository, dispatcher = dispatcher)
        viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
        testScheduler.advanceUntilIdle()
        viewModel.dispatch(CatchRecordFlowEvent.ResumeDraft)
        testScheduler.advanceUntilIdle()
        return Triple(viewModel, gearOne, gearTwo)
    }

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
    fun `same port shortcut yes sets both ports and skips to gear search`() =
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
                assertEquals(WizardStep.GearSearch, updated.currentStep)
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

    @Test
    fun `gear type selected records pending gear type without persisting`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            val viewModel = buildViewModel(repository = repository, dispatcher = dispatcher)
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
            testScheduler.advanceUntilIdle()
            viewModel.dispatch(CatchRecordFlowEvent.VesselSelected("vessel-achilles"))
            testScheduler.advanceUntilIdle()

            viewModel.state.test {
                skipItems(1)
                viewModel.dispatch(CatchRecordFlowEvent.GearTypeSelected("gear-seine-nets"))
                val updated = awaitItem()
                assertEquals("gear-seine-nets", updated.pendingGearTypeId)
            }
            // Not yet persisted: the draft in the repository has no gear uses until measurements submit.
            val activeDraft = repository.getActiveDraft("vessel-achilles").getOrThrow()
            assertTrue(activeDraft?.gearUses.orEmpty().isEmpty())
        }

    @Test
    fun `gear measurements submitted appends a new gear use and advances to gear summary`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            val viewModel =
                buildViewModel(repository = repository, idFactory = { "gear-use-1" }, dispatcher = dispatcher)
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
            testScheduler.advanceUntilIdle()
            viewModel.dispatch(CatchRecordFlowEvent.VesselSelected("vessel-achilles"))
            testScheduler.advanceUntilIdle()
            viewModel.dispatch(CatchRecordFlowEvent.GearTypeSelected("gear-seine-nets"))
            testScheduler.advanceUntilIdle()

            viewModel.state.test {
                skipItems(1)
                viewModel.dispatch(
                    CatchRecordFlowEvent.GearMeasurementsSubmitted(
                        mapOf(GearMeasurementFieldKeys.MESH_SIZE_MM to MeasurementValue.Numeric(100.0, "mm")),
                    ),
                )
                assertEquals(UiStatus.Loading, awaitItem().status)
                val updated = awaitItem()
                assertEquals(WizardStep.GearSummary, updated.currentStep)
                assertNull(updated.pendingGearTypeId)
                val draft = (updated.status as UiStatus.Content<CatchRecordDraft>).value
                assertEquals(1, draft.gearUses.size)
                val gearUse = draft.gearUses.single()
                assertEquals("gear-use-1", gearUse.id)
                assertEquals("gear-seine-nets", gearUse.gearTypeId)
                assertEquals(
                    MeasurementValue.Numeric(100.0, "mm"),
                    gearUse.measurements[GearMeasurementFieldKeys.MESH_SIZE_MM],
                )
                assertTrue(!gearUse.confirmedUsedOnTrip)
                assertNull(gearUse.numberOfShots)
            }
        }

    @Test
    fun `gear removed persists filtered gear list without changing step`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            val started = repository.startDraft("vessel-achilles").getOrThrow()
            val gearUseToKeep =
                GearUse(id = "gear-use-keep", gearTypeId = "gear-seine-nets", statisticalSubRectangleCode = null)
            val gearUseToRemove =
                GearUse(id = "gear-use-remove", gearTypeId = "gear-seine-nets", statisticalSubRectangleCode = null)
            repository
                .saveDraft(
                    started.copy(
                        isTripToday = true,
                        departurePort = PortSelection("port-hastings", PortSelectionMode.Favourite),
                        returnPort = PortSelection("port-hastings", PortSelectionMode.Favourite),
                        gearUses = listOf(gearUseToKeep, gearUseToRemove),
                    ),
                ).getOrThrow()

            val viewModel = buildViewModel(repository = repository, dispatcher = dispatcher)
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
            testScheduler.advanceUntilIdle()
            viewModel.dispatch(CatchRecordFlowEvent.ResumeDraft)
            testScheduler.advanceUntilIdle()

            viewModel.state.test {
                val current = awaitItem()
                val currentDraft = (current.status as UiStatus.Content<CatchRecordDraft>).value
                val updatedDraft = currentDraft.copy(gearUses = listOf(gearUseToKeep))
                viewModel.dispatch(CatchRecordFlowEvent.GearRemoved(updatedDraft))
                assertEquals(UiStatus.Loading, awaitItem().status)
                val afterRemoval = awaitItem()
                assertEquals(WizardStep.GearSummary, afterRemoval.currentStep)
                val draft = (afterRemoval.status as UiStatus.Content<CatchRecordDraft>).value
                assertEquals(listOf("gear-use-keep"), draft.gearUses.map { it.id })
            }
        }

    /**
     * Phase 4/5 interleaved per-gear loop: with two confirmed gears, each gear's stat-rectangle step must
     * complete *and* its species step must complete before the next gear's stat-rectangle step is ever
     * reached (see [nextGearUsePendingStatRectangle]/[nextGearUsePendingSpecies]/[nextWizardStepForDraft]).
     * Once every confirmed gear has both, the flow proceeds to the Phase 5B trip-level
     * [WizardStep.NotLandedStraightAwayDecision] step (not per-gear) — answering `false` there advances
     * straight to [WizardStep.CheckYourAnswers] (Phase 6/7 not yet built), skipping
     * [WizardStep.NotLandedStraightAwaySpecies] entirely.
     */
    @Suppress("LongMethod")
    @Test
    fun `interleaved gear loop completes both gears rectangle and species before 5B decision`() =
        runTest {
            val (viewModel, gearOne, gearTwo) = setUpTwoConfirmedGearsAwaitingStatRectangle()

            viewModel.state.test {
                val current = awaitItem()
                val currentDraft = (current.status as UiStatus.Content<CatchRecordDraft>).value
                assertEquals(WizardStep.GearStatRectangle, current.currentStep)
                assertEquals(gearOne.id, nextGearUsePendingStatRectangle(currentDraft)?.id)

                // Save gear one's rectangle: gear one's own species step is next, NOT gear two's rectangle.
                val afterGearOneRectangle =
                    currentDraft.copy(
                        gearUses =
                            currentDraft.gearUses.map {
                                if (it.id == gearOne.id) it.copy(statisticalSubRectangleCode = "38E95") else it
                            },
                    )
                viewModel.dispatch(
                    CatchRecordFlowEvent.SaveAndContinue(
                        afterGearOneRectangle,
                        nextWizardStepForDraft(afterGearOneRectangle),
                    ),
                )
                assertEquals(UiStatus.Loading, awaitItem().status)
                val afterGearOneRectangleState = awaitItem()
                assertEquals(WizardStep.GearSpeciesSearch, afterGearOneRectangleState.currentStep)
                val draftAfterGearOneRectangle =
                    (afterGearOneRectangleState.status as UiStatus.Content<CatchRecordDraft>).value
                assertEquals(gearOne.id, nextGearUsePendingSpecies(draftAfterGearOneRectangle)?.id)

                // Confirm gear one's species: gear two's rectangle step is next.
                val afterGearOneSpecies =
                    draftAfterGearOneRectangle.copy(
                        gearUses =
                            draftAfterGearOneRectangle.gearUses.map {
                                if (it.id == gearOne.id) {
                                    it.copy(
                                        speciesWeights =
                                            listOf(
                                                SpeciesWeightEntry(
                                                    id = "sw-1",
                                                    speciesId = "species-cod",
                                                    confirmedCaught = true,
                                                ),
                                            ),
                                    )
                                } else {
                                    it
                                }
                            },
                    )
                viewModel.dispatch(
                    CatchRecordFlowEvent.SaveAndContinue(
                        afterGearOneSpecies,
                        nextWizardStepForDraft(afterGearOneSpecies),
                    ),
                )
                assertEquals(UiStatus.Loading, awaitItem().status)
                val afterGearOneSpeciesState = awaitItem()
                assertEquals(WizardStep.GearStatRectangle, afterGearOneSpeciesState.currentStep)
                val draftAfterGearOneSpecies =
                    (afterGearOneSpeciesState.status as UiStatus.Content<CatchRecordDraft>).value
                assertEquals(gearTwo.id, nextGearUsePendingStatRectangle(draftAfterGearOneSpecies)?.id)

                // Save gear two's rectangle: gear two's own species step is next.
                val afterGearTwoRectangle =
                    draftAfterGearOneSpecies.copy(
                        gearUses =
                            draftAfterGearOneSpecies.gearUses.map {
                                if (it.id == gearTwo.id) it.copy(statisticalSubRectangleCode = "38E98") else it
                            },
                    )
                viewModel.dispatch(
                    CatchRecordFlowEvent.SaveAndContinue(
                        afterGearTwoRectangle,
                        nextWizardStepForDraft(afterGearTwoRectangle),
                    ),
                )
                assertEquals(UiStatus.Loading, awaitItem().status)
                val afterGearTwoRectangleState = awaitItem()
                assertEquals(WizardStep.GearSpeciesSearch, afterGearTwoRectangleState.currentStep)

                // Confirm gear two's species: every confirmed gear is now fully done, so the trip-level
                // Phase 5B decision step is next — not another per-gear step.
                val draftAfterGearTwoRectangle =
                    (afterGearTwoRectangleState.status as UiStatus.Content<CatchRecordDraft>).value
                val afterGearTwoSpecies =
                    draftAfterGearTwoRectangle.copy(
                        gearUses =
                            draftAfterGearTwoRectangle.gearUses.map {
                                if (it.id == gearTwo.id) {
                                    it.copy(
                                        speciesWeights =
                                            listOf(
                                                SpeciesWeightEntry(
                                                    id = "sw-2",
                                                    speciesId = "species-plaice",
                                                    confirmedCaught = true,
                                                ),
                                            ),
                                    )
                                } else {
                                    it
                                }
                            },
                    )
                viewModel.dispatch(
                    CatchRecordFlowEvent.SaveAndContinue(
                        afterGearTwoSpecies,
                        nextWizardStepForDraft(afterGearTwoSpecies),
                    ),
                )
                assertEquals(UiStatus.Loading, awaitItem().status)
                val afterGearTwoSpeciesState = awaitItem()
                assertEquals(WizardStep.NotLandedStraightAwayDecision, afterGearTwoSpeciesState.currentStep)
                val draftAfterGearTwoSpecies =
                    (afterGearTwoSpeciesState.status as UiStatus.Content<CatchRecordDraft>).value
                assertNull(nextGearUsePendingStatRectangle(draftAfterGearTwoSpecies))
                assertNull(nextGearUsePendingSpecies(draftAfterGearTwoSpecies))

                // Answering "No" to the 5B decision skips WizardStep.NotLandedStraightAwaySpecies entirely,
                // landing on the Phase 8 check-your-answers step directly (Phase 6/7 not yet built — see
                // resolveSubmissionStep's TODO).
                val afterDecision = draftAfterGearTwoSpecies.copy(notLandedStraightAway = false)
                viewModel.dispatch(
                    CatchRecordFlowEvent.SaveAndContinue(afterDecision, nextWizardStepForDraft(afterDecision)),
                )
                assertEquals(UiStatus.Loading, awaitItem().status)
                val afterDecisionState = awaitItem()
                assertEquals(WizardStep.CheckYourAnswers, afterDecisionState.currentStep)
            }
        }

    // --- Phase 8: accept-declaration-and-submit online/offline branching -------------------------

    @Test
    fun `accepting the declaration while online and submission succeeds routes to submission success`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            val started = repository.startDraft("vessel-achilles").getOrThrow()
            repository.saveDraft(started.asSubmissionReady()).getOrThrow()
            val submissionRepository = FakeCatchRecordSubmissionRepository(shouldSucceed = true)
            val connectivityChecker = FakeNetworkConnectivityChecker(connected = true)
            val syncScheduler = FakeCatchRecordSyncScheduler()
            val viewModel =
                buildViewModel(
                    repository = repository,
                    submissionRepository = submissionRepository,
                    connectivityChecker = connectivityChecker,
                    syncScheduler = syncScheduler,
                    dispatcher = dispatcher,
                )
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
            testScheduler.advanceUntilIdle()
            viewModel.dispatch(CatchRecordFlowEvent.ResumeDraft)
            testScheduler.advanceUntilIdle()

            viewModel.state.test {
                awaitItem()
                viewModel.dispatch(CatchRecordFlowEvent.AcceptDeclarationAndSubmit)
                assertEquals(UiStatus.Loading, awaitItem().status)
                val submitted = awaitItem()
                assertEquals(WizardStep.SubmissionSuccess, submitted.currentStep)
                val draft = (submitted.status as UiStatus.Content<CatchRecordDraft>).value
                assertEquals(DraftStatus.Submitted, draft.status)
            }
            assertEquals(listOf(started.id), submissionRepository.submittedDrafts.map { it.id })
            assertTrue(syncScheduler.scheduledDraftIds.isEmpty())
        }

    @Test
    fun `accepting the declaration while online but the submission fails falls back to pending sync`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            val started = repository.startDraft("vessel-achilles").getOrThrow()
            repository.saveDraft(started.asSubmissionReady()).getOrThrow()
            val submissionRepository = FakeCatchRecordSubmissionRepository(shouldSucceed = false)
            val connectivityChecker = FakeNetworkConnectivityChecker(connected = true)
            val syncScheduler = FakeCatchRecordSyncScheduler()
            val viewModel =
                buildViewModel(
                    repository = repository,
                    submissionRepository = submissionRepository,
                    connectivityChecker = connectivityChecker,
                    syncScheduler = syncScheduler,
                    dispatcher = dispatcher,
                )
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
            testScheduler.advanceUntilIdle()
            viewModel.dispatch(CatchRecordFlowEvent.ResumeDraft)
            testScheduler.advanceUntilIdle()

            viewModel.state.test {
                awaitItem()
                viewModel.dispatch(CatchRecordFlowEvent.AcceptDeclarationAndSubmit)
                assertEquals(UiStatus.Loading, awaitItem().status)
                val submitted = awaitItem()
                assertEquals(WizardStep.SubmissionPendingSync, submitted.currentStep)
                val draft = (submitted.status as UiStatus.Content<CatchRecordDraft>).value
                assertEquals(DraftStatus.PendingSync, draft.status)
            }
            assertEquals(listOf(started.id), syncScheduler.scheduledDraftIds)
        }

    @Test
    fun `accepting the declaration while offline never attempts a submission and enqueues pending sync`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val repository = FakeCatchRecordDraftRepository(idFactory = { "draft-1" })
            val started = repository.startDraft("vessel-achilles").getOrThrow()
            repository.saveDraft(started.asSubmissionReady()).getOrThrow()
            val submissionRepository = FakeCatchRecordSubmissionRepository(shouldSucceed = true)
            val connectivityChecker = FakeNetworkConnectivityChecker(connected = false)
            val syncScheduler = FakeCatchRecordSyncScheduler()
            val viewModel =
                buildViewModel(
                    repository = repository,
                    submissionRepository = submissionRepository,
                    connectivityChecker = connectivityChecker,
                    syncScheduler = syncScheduler,
                    dispatcher = dispatcher,
                )
            viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
            testScheduler.advanceUntilIdle()
            viewModel.dispatch(CatchRecordFlowEvent.ResumeDraft)
            testScheduler.advanceUntilIdle()

            viewModel.state.test {
                awaitItem()
                viewModel.dispatch(CatchRecordFlowEvent.AcceptDeclarationAndSubmit)
                assertEquals(UiStatus.Loading, awaitItem().status)
                val submitted = awaitItem()
                assertEquals(WizardStep.SubmissionPendingSync, submitted.currentStep)
                val draft = (submitted.status as UiStatus.Content<CatchRecordDraft>).value
                assertEquals(DraftStatus.PendingSync, draft.status)
            }
            assertTrue(submissionRepository.submittedDrafts.isEmpty())
            assertEquals(listOf(started.id), syncScheduler.scheduledDraftIds)
        }
}
