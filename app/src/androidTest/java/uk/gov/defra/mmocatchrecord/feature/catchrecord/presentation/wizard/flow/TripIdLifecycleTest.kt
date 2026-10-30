package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.core.connectivity.NetworkConnectivityChecker
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local.CatchRecordDatabase
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local.CatchRecordReferenceGenerator
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local.RoomCatchRecordDraftRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.StubReferenceDataRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraftFactory
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordSubmissionRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordSyncScheduler
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DmyDate
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.MeasurementValue
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelection
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelectionMode
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.SpeciesWeightEntry
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldKeys
import java.time.Instant

/** Trip ID story AC01–AC08: real ViewModel + in-memory Room, fixed Europe/London clock. */
class TripIdLifecycleTest {
    private lateinit var database: CatchRecordDatabase
    private lateinit var repository: RoomCatchRecordDraftRepository
    private var clockMillis = Instant.parse("2026-09-20T10:22:33Z").toEpochMilli()
    private var idCounter = 0

    private class FakeConnectivity(
        var connected: Boolean,
    ) : NetworkConnectivityChecker {
        override fun isConnected(): Boolean = connected
    }

    private class CapturingSubmission : CatchRecordSubmissionRepository {
        val submitted = mutableListOf<CatchRecordDraft>()

        override suspend fun submit(draft: CatchRecordDraft): Result<Unit> {
            submitted += draft
            return Result.success(Unit)
        }
    }

    private class RecordingScheduler : CatchRecordSyncScheduler {
        val scheduled = mutableListOf<String>()

        override fun scheduleSync(draftId: String) {
            scheduled += draftId
        }
    }

    private val connectivity = FakeConnectivity(connected = true)
    private val submission = CapturingSubmission()
    private val scheduler = RecordingScheduler()

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    CatchRecordDatabase::class.java,
                ).build()
        repository = RoomCatchRecordDraftRepository(database.catchRecordDraftDao()) { clockMillis }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun newViewModel(): CatchRecordFlowViewModel =
        CatchRecordFlowViewModel(
            repository,
            StubReferenceDataRepository(),
            submission,
            connectivity,
            scheduler,
            { clockMillis },
            { "draft-${idCounter++}" },
            Dispatchers.Default,
        )

    private fun awaitState(
        viewModel: CatchRecordFlowViewModel,
        condition: (CatchRecordFlowViewState) -> Boolean,
    ): CatchRecordFlowViewState =
        runBlocking {
            withTimeout(TIMEOUT_MS) {
                while (!condition(viewModel.state.value)) delay(POLL_MS)
                viewModel.state.value
            }
        }

    private fun CatchRecordFlowViewState.draft(): CatchRecordDraft? =
        (status as? UiStatus.Content<*>)?.value as? CatchRecordDraft

    private fun startRecord(
        viewModel: CatchRecordFlowViewModel,
        vesselId: String = VESSEL_ACHILLES,
    ): CatchRecordDraft {
        viewModel.dispatch(CatchRecordFlowEvent.EnterFlow)
        awaitState(viewModel) { it.status !is UiStatus.Loading }
        viewModel.dispatch(CatchRecordFlowEvent.VesselSelected(vesselId))
        return awaitState(viewModel) { it.currentStep == WizardStep.TripToday && it.draft() != null }.draft()!!
    }

    private fun completeDraft(draft: CatchRecordDraft): CatchRecordDraft =
        draft.copy(
            isTripToday = false,
            departureDate = DmyDate(19, 9, 2026),
            returnDate = DmyDate(20, 9, 2026),
            departurePort = PortSelection(portId = "port-hastings", selectionMode = PortSelectionMode.FirstTime),
            returnPort = PortSelection(portId = "port-hastings", selectionMode = PortSelectionMode.FirstTime),
            gearUses =
                listOf(
                    GearUse(
                        id = "gear-use-1",
                        gearTypeId = "gear-seine-nets",
                        statisticalSubRectangleCode = "38E95",
                        measurements =
                            mapOf(GearMeasurementFieldKeys.MESH_SIZE_MM to MeasurementValue.Numeric(50.0, "mm")),
                        speciesWeights =
                            listOf(
                                SpeciesWeightEntry(
                                    id = "sw-1",
                                    speciesId = "species-cod",
                                    weightAboveMinimumSizeKg = 12.5,
                                    confirmedCaught = true,
                                ),
                            ),
                        numberOfShots = 3,
                        confirmedUsedOnTrip = true,
                    ),
                ),
            notLandedStraightAway = false,
            lateSubmissionWarningAcknowledged = true,
        )

    private fun saveAndGoToCheckAnswers(
        viewModel: CatchRecordFlowViewModel,
        draft: CatchRecordDraft,
    ): CatchRecordDraft {
        viewModel.dispatch(CatchRecordFlowEvent.SaveAndContinue(draft, WizardStep.CheckYourAnswers))
        return awaitState(viewModel) { it.currentStep == WizardStep.CheckYourAnswers }.draft()!!
    }

    private fun submit(viewModel: CatchRecordFlowViewModel): CatchRecordFlowViewState {
        viewModel.dispatch(CatchRecordFlowEvent.AcceptDeclarationAndSubmit)
        return awaitState(viewModel) {
            it.currentStep == WizardStep.SubmissionSuccess ||
                it.currentStep == WizardStep.SubmissionPendingSync ||
                it.status is UiStatus.Error
        }
    }

    private fun submitNewRecord(): CatchRecordDraft {
        val viewModel = newViewModel()
        val created = startRecord(viewModel)
        saveAndGoToCheckAnswers(viewModel, completeDraft(created))
        val result = submit(viewModel)
        assertEquals(WizardStep.SubmissionSuccess, result.currentStep)
        return stored(created.id)
    }

    private fun stored(id: String): CatchRecordDraft = runBlocking { repository.getDraftById(id).getOrThrow()!! }

    // --- AC01 – Generate Trip ID on record creation ------------------------------------------------

    @Test
    fun ac01_tripIdIsGeneratedInRssDdmmyyyyHhmmssFormatWhenRecordIsInitiated() {
        val created = startRecord(newViewModel())

        assertTrue(TRIP_ID_FORMAT.matches(created.catchRecordReference!!))
        assertTrue(created.catchRecordReference!!.endsWith(TRIP_TIME_SUFFIX))
    }

    @Test
    fun ac01_generatorProducesTicketExampleForRssB14974() {
        val reference = CatchRecordReferenceGenerator.generate("B14974", clockMillis)

        assertEquals("B14974-20092026-112233", reference)
    }

    @Test
    fun ac01_tripIdIsPersistedBeforeAnyCatchDataIsEntered() {
        val created = startRecord(newViewModel())
        val persisted = stored(created.id)

        assertEquals(created.catchRecordReference, persisted.catchRecordReference)
        assertNull(persisted.isTripToday)
        assertNull(persisted.departureDate)
        assertNull(persisted.returnDate)
        assertNull(persisted.departurePort)
        assertTrue(persisted.gearUses.isEmpty())
    }

    // --- AC02 – Persist Trip ID for draft records ---------------------------------------------------

    @Test
    fun ac02_savingDraftAcrossStepsKeepsTheSameTripId() {
        val viewModel = newViewModel()
        val created = startRecord(viewModel)
        clockMillis += 65_000
        viewModel.dispatch(CatchRecordFlowEvent.TripTodayAnswered(isTripToday = false))
        awaitState(viewModel) { it.currentStep == WizardStep.DepartureDate }
        clockMillis += 65_000
        val saved = saveAndGoToCheckAnswers(viewModel, completeDraft(viewModel.state.value.draft()!!))

        assertEquals(created.catchRecordReference, saved.catchRecordReference)
        assertEquals(created.catchRecordReference, stored(created.id).catchRecordReference)
    }

    @Test
    fun ac02_resumingDraftLaterDoesNotGenerateANewTripId() {
        val created = startRecord(newViewModel())
        clockMillis += 3_600_000
        val resumed = startRecord(newViewModel())

        assertEquals(created.id, resumed.id)
        assertEquals(created.catchRecordReference, resumed.catchRecordReference)
        val all = runBlocking { repository.observeRecordSummaries().first() }
        assertEquals(1, all.size)
    }

    // --- AC03 – Retain Trip ID during offline working -----------------------------------------------

    @Test
    fun ac03_recordCompletedOfflineIsSavedLocallyWithTheSameTripId() {
        val viewModel = newViewModel()
        val created = startRecord(viewModel)
        connectivity.connected = false
        saveAndGoToCheckAnswers(viewModel, completeDraft(created))
        val result = submit(viewModel)

        assertEquals(WizardStep.SubmissionPendingSync, result.currentStep)
        val local = stored(created.id)
        assertEquals(DraftStatus.PendingSync, local.status)
        assertEquals(created.catchRecordReference, local.catchRecordReference)
        assertTrue(submission.submitted.isEmpty())
    }

    @Test
    fun ac03_queuedSyncUsesTheExistingTripIdWhenConnectivityReturns() {
        val viewModel = newViewModel()
        val created = startRecord(viewModel)
        connectivity.connected = false
        saveAndGoToCheckAnswers(viewModel, completeDraft(created))
        submit(viewModel)

        assertEquals(listOf(created.id), scheduler.scheduled)
        val queued = stored(scheduler.scheduled.single())
        runBlocking { submission.submit(queued) }
        assertEquals(created.catchRecordReference, submission.submitted.single().catchRecordReference)
    }

    // --- AC04 – Use Trip ID during submission -------------------------------------------------------

    @Test
    fun ac04_onlineSubmissionIncludesTheTripIdAndStoresItAgainstSubmittedRecord() {
        val submitted = submitNewRecord()

        assertTrue(TRIP_ID_FORMAT.matches(submitted.catchRecordReference!!))
        assertEquals(submitted.catchRecordReference, submission.submitted.single().catchRecordReference)
        assertEquals(DraftStatus.Submitted, submitted.status)
    }

    // --- AC05 – Prevent duplicate record creation ---------------------------------------------------

    @Test
    fun ac05_secondRecordCarryingAnExistingTripIdIsRejected() {
        val existing = submitNewRecord()
        val duplicate =
            CatchRecordDraftFactory
                .newDraft(VESSEL_HERCULES, "duplicate-id", existing.catchRecordReference!!)
                .copy(modifiedAtEpochMillis = clockMillis)

        val result = runBlocking { repository.saveDraft(duplicate) }

        assertTrue(result.isFailure)
        assertNull(runBlocking { repository.getDraftById("duplicate-id").getOrThrow() })
    }

    @Test
    fun ac05_reSubmittingTheSameRecordDoesNotCreateADuplicate() {
        val submitted = submitNewRecord()
        val viewModel = newViewModel()
        viewModel.dispatch(CatchRecordFlowEvent.EnterFlowForDraft(submitted.id))
        awaitState(viewModel) { it.draft()?.id == submitted.id }
        submit(viewModel)

        val summaries = runBlocking { repository.observeRecordSummaries().first() }
        assertEquals(1, summaries.count { it.catchRecordReference == submitted.catchRecordReference })
    }

    // --- AC06 – Preserve Trip ID after successful submission ----------------------------------------

    @Test
    fun ac06_viewingSubmittedRecordShowsTheOriginalUnchangedTripId() {
        val submitted = submitNewRecord()
        clockMillis += 86_400_000
        val viewModel = newViewModel()
        viewModel.dispatch(CatchRecordFlowEvent.EnterFlowForDraft(submitted.id))
        val viewed = awaitState(viewModel) { it.draft()?.id == submitted.id }

        assertEquals(submitted.catchRecordReference, viewed.catchRecordReference)
        val summary = runBlocking { repository.observeRecordSummaries().first() }.single()
        assertEquals(submitted.catchRecordReference, summary.catchRecordReference)
    }

    // --- AC07 – Preserve Trip ID during amendments --------------------------------------------------

    @Test
    fun ac07_amendedAndResubmittedRecordRetainsTheOriginalTripId() {
        val submitted = submitNewRecord()
        clockMillis += 86_400_000
        val viewModel = newViewModel()
        viewModel.dispatch(CatchRecordFlowEvent.EnterFlowForDraft(submitted.id))
        val loaded = awaitState(viewModel) { it.draft()?.id == submitted.id }.draft()!!
        val amended = loaded.copy(gearUses = loaded.gearUses.map { it.copy(numberOfShots = 5) })
        saveAndGoToCheckAnswers(viewModel, amended)
        val result = submit(viewModel)

        assertEquals("Amend re-submit status: ${result.status}", WizardStep.SubmissionSuccess, result.currentStep)
        assertEquals(2, submission.submitted.size)
        assertEquals(submitted.catchRecordReference, submission.submitted.last().catchRecordReference)
        val afterAmend = stored(submitted.id)
        assertEquals(5, afterAmend.gearUses.single().numberOfShots)
        assertEquals(submitted.catchRecordReference, afterAmend.catchRecordReference)
    }

    // --- AC08 – Ensure Trip ID uniqueness -----------------------------------------------------------

    @Test
    fun ac08_recordsCreatedInTheSameSecondForDifferentVesselsGetDifferentTripIds() {
        val achilles = startRecord(newViewModel(), VESSEL_ACHILLES)
        val hercules = startRecord(newViewModel(), VESSEL_HERCULES)

        assertNotEquals(achilles.catchRecordReference, hercules.catchRecordReference)
        assertTrue(hercules.catchRecordReference!!.endsWith(TRIP_TIME_SUFFIX))
    }

    @Test
    fun ac08_newRecordInSameSecondAsASubmittedOneIsNotGivenTheSameTripId() {
        val submitted = submitNewRecord()
        val next = startRecord(newViewModel())

        assertNotEquals(submitted.id, next.id)
        assertNotEquals(submitted.catchRecordReference, next.catchRecordReference)
        assertTrue(TRIP_ID_FORMAT.matches(next.catchRecordReference!!))
    }

    @Test
    fun ac08_noDraftSubmittedOrPendingRecordsShareATripId() {
        submitNewRecord()
        val pendingVm = newViewModel()
        val pending = startRecord(pendingVm, VESSEL_HERCULES)
        connectivity.connected = false
        saveAndGoToCheckAnswers(pendingVm, completeDraft(pending))
        submit(pendingVm)
        connectivity.connected = true
        startRecord(newViewModel(), VESSEL_ACHILLES)

        val references = runBlocking { repository.observeRecordSummaries().first() }.map { it.catchRecordReference }
        assertEquals(3, references.size)
        assertEquals(references.size, references.toSet().size)
    }

    private companion object {
        const val VESSEL_ACHILLES = "vessel-achilles"
        const val VESSEL_HERCULES = "vessel-hercules"
        const val TIMEOUT_MS = 5_000L
        const val POLL_MS = 20L
        const val TRIP_TIME_SUFFIX = "-20092026-112233"
        val TRIP_ID_FORMAT = Regex("^[A-Z0-9]+-\\d{8}-\\d{6}$")
    }
}
