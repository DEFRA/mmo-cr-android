// `dispatch`'s cyclomatic complexity grows by exactly one flat `when` branch per wizard event (each a
// one-line delegation to its own private handler, not nested logic) — same rationale as the existing
// `TooManyFunctions` suppression: it scales with the number of wizard steps/events, not with genuine
// per-branch complexity, so a file-level suppression is more honest than an arbitrary split.
@file:Suppress("detekt.TooManyFunctions", "detekt.MaxLineLength", "detekt.CyclomaticComplexMethod")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow

import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import timber.log.Timber
import uk.gov.defra.mmocatchrecord.core.architecture.BaseViewModel
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.core.connectivity.NetworkConnectivityChecker
import uk.gov.defra.mmocatchrecord.core.error.SafeErrorMapper
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraftRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraftValidation
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordSubmissionRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordSyncScheduler
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DmyDate
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftInvalidation
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftSubmissionValidation
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.MeasurementValue
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelection
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelectionMode
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.SpeciesWeightEntry
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.MapGeometryRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Port
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.ReferenceDataRepository
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject

private const val SUBMISSION_VALIDATION_MESSAGE =
    "Your catch record is incomplete. Go back and complete every step before submitting."

/** Nav-graph-scoped ViewModel shared by every catch-record wizard screen. */
@HiltViewModel
@Suppress("TooManyFunctions", "LongParameterList")
class CatchRecordFlowViewModel
    @Inject
    constructor(
        private val draftRepository: CatchRecordDraftRepository,
        private val referenceDataRepository: ReferenceDataRepository,
        private val mapGeometryRepository: MapGeometryRepository,
        private val submissionRepository: CatchRecordSubmissionRepository,
        private val connectivityChecker: NetworkConnectivityChecker,
        private val syncScheduler: CatchRecordSyncScheduler,
        private val clock: () -> Long,
        private val idFactory: () -> String,
        defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    ) : BaseViewModel<CatchRecordFlowViewState, CatchRecordFlowEvent>(
            initialState = CatchRecordFlowViewState(),
            defaultDispatcher = defaultDispatcher,
        ) {
        /**
         * The most recent event whose handling failed, tracked so [CatchRecordFlowEvent.Retry] can safely
         * re-attempt it — see `WizardErrorState`'s Retry control. Deliberately **not** part of
         * [CatchRecordFlowViewState] (an event is not an immutable, comparable presentation value); this is
         * ViewModel-internal orchestration state only.
         */
        private var lastFailedEvent: CatchRecordFlowEvent? = null

        override fun dispatch(event: CatchRecordFlowEvent) {
            when (event) {
                CatchRecordFlowEvent.EnterFlow -> enterFlow()
                CatchRecordFlowEvent.ResumeDraft -> resumeDraft()
                CatchRecordFlowEvent.DeleteDraft -> deleteDraft()
                is CatchRecordFlowEvent.VesselSelected -> vesselSelected(event.vesselId)
                is CatchRecordFlowEvent.TripTodayAnswered -> tripTodayAnswered(event.isTripToday)
                CatchRecordFlowEvent.SamePortShortcutDeclined -> declineSamePortShortcut()
                CatchRecordFlowEvent.SamePortShortcutAccepted -> acceptSamePortShortcut()
                is CatchRecordFlowEvent.SaveAndContinue -> saveAndContinue(event.updatedDraft, event.nextStep)
                is CatchRecordFlowEvent.GearTypeSelected -> gearTypeSelected(event.gearTypeId)
                is CatchRecordFlowEvent.GearMeasurementsSubmitted -> gearMeasurementsSubmitted(event.measurements)
                is CatchRecordFlowEvent.GearRemoved -> persistDraftWithoutStepChange(event, event.updatedDraft)
                is CatchRecordFlowEvent.SpeciesAddedToCurrentGear -> speciesAddedToCurrentGear(event.speciesId)
                is CatchRecordFlowEvent.SpeciesRemoved -> persistDraftWithoutStepChange(event, event.updatedDraft)
                CatchRecordFlowEvent.MarkReadyToSubmit -> markReadyToSubmit()
                CatchRecordFlowEvent.AcceptDeclarationAndSubmit -> acceptDeclarationAndSubmit()
                CatchRecordFlowEvent.Retry -> retryLastFailedEvent()
                is CatchRecordFlowEvent.EditGearMeasurements -> editGearMeasurements(event)
                is CatchRecordFlowEvent.EditGearStatRectangle -> editGearStatRectangle(event)
                is CatchRecordFlowEvent.SpeciesAddedToGearUse -> speciesAddedToGearUse(event)
                is CatchRecordFlowEvent.EditGearSpeciesWeights -> editGearSpeciesWeights(event)
                CatchRecordFlowEvent.LoadMapGeometry -> loadMapGeometry()
            }
        }

        private fun enterFlow() {
            updateState { it.copy(status = UiStatus.Loading) }
            launchInViewModelScope {
                runCatching {
                    val vessels = referenceDataRepository.getVessels().getOrThrow()
                    val ports = referenceDataRepository.getPorts().getOrThrow()
                    val gearTypes = referenceDataRepository.getGearTypes().getOrThrow()
                    val statisticalSubRectangles = referenceDataRepository.getStatisticalSubRectangles().getOrThrow()
                    val species = referenceDataRepository.getSpecies().getOrThrow()
                    val existingDraft = draftRepository.getAnyActiveDraft().getOrThrow()
                    val previouslyUsedPorts =
                        existingDraft
                            ?.let {
                                referenceDataRepository
                                    .getPreviouslyUsedPorts(
                                        it.vesselId,
                                    ).getOrThrow()
                            }.orEmpty()
                    updateState {
                        it.copy(
                            status = existingDraft?.let { UiStatus.Content(it) } ?: UiStatus.Idle,
                            currentStep =
                                if (existingDraft ==
                                    null
                                ) {
                                    WizardStep.VesselSelection
                                } else {
                                    WizardStep.DraftResume
                                },
                            vessels = vessels,
                            ports = ports,
                            gearTypes = gearTypes,
                            statisticalSubRectangles = statisticalSubRectangles,
                            species = species,
                            previouslyUsedPorts = previouslyUsedPorts,
                            departurePortEntryMode = deriveDeparturePortEntryMode(previouslyUsedPorts),
                            samePortCandidate = previouslyUsedPorts.firstOrNull(),
                        )
                    }
                }.onFailure { emitLoadError(CatchRecordFlowEvent.EnterFlow, it) }
            }
        }

        private fun resumeDraft() {
            val draft = currentDraftOrNull() ?: return
            updateState {
                it.copy(currentStep = nextWizardStepForDraft(draft, clock()), status = UiStatus.Content(draft))
            }
        }

        private fun deleteDraft() {
            val draft = currentDraftOrNull() ?: return
            updateState { it.copy(status = UiStatus.Loading) }
            launchInViewModelScope {
                draftRepository.deleteDraft(draft.id).fold(
                    onSuccess = {
                        updateState {
                            it.copy(
                                status = UiStatus.Idle,
                                currentStep = WizardStep.VesselSelection,
                                previouslyUsedPorts = emptyList(),
                                departurePortEntryMode = DeparturePortEntryMode.Search,
                                samePortCandidate = null,
                            )
                        }
                    },
                    onFailure = { emitLoadError(CatchRecordFlowEvent.DeleteDraft, it) },
                )
            }
        }

        private fun vesselSelected(vesselId: String) {
            updateState { it.copy(status = UiStatus.Loading) }
            launchInViewModelScope {
                runCatching {
                    val draft = draftRepository.startDraft(vesselId).getOrThrow()
                    val previouslyUsedPorts = referenceDataRepository.getPreviouslyUsedPorts(vesselId).getOrThrow()
                    emitLoadedDraft(draft, WizardStep.TripToday, previouslyUsedPorts)
                }.onFailure { emitLoadError(CatchRecordFlowEvent.VesselSelected(vesselId), it) }
            }
        }

        private fun tripTodayAnswered(isTripToday: Boolean) {
            val draft = currentDraftOrNull() ?: return
            val updatedDraft =
                if (isTripToday) {
                    val today = currentDate()
                    draft.copy(isTripToday = true, departureDate = today, returnDate = today)
                } else {
                    draft.copy(isTripToday = false, departureDate = null, returnDate = null)
                }
            saveAndContinue(updatedDraft, if (isTripToday) WizardStep.DeparturePort else WizardStep.DepartureDate)
        }

        private fun declineSamePortShortcut() {
            updateState {
                it.copy(
                    departurePortEntryMode =
                        if (it.previouslyUsedPorts.isEmpty()) {
                            DeparturePortEntryMode.Search
                        } else {
                            DeparturePortEntryMode.FavouriteList
                        },
                )
            }
        }

        private fun acceptSamePortShortcut() {
            val draft = currentDraftOrNull() ?: return
            val selectedPort = currentState.samePortCandidate ?: return
            val updatedDraft =
                draft.copy(
                    departurePort = PortSelection(selectedPort.id, PortSelectionMode.Favourite),
                    returnPort = PortSelection(selectedPort.id, PortSelectionMode.Favourite),
                )
            saveAndContinue(updatedDraft, WizardStep.GearSearch)
        }

        private fun saveAndContinue(
            updatedDraft: CatchRecordDraft,
            nextStep: WizardStep,
        ) {
            val previousDraft = currentDraftOrNull()
            updateState { it.copy(status = UiStatus.Loading) }
            launchInViewModelScope {
                val reconciled = DraftInvalidation.reconcile(previousDraft, updatedDraft)
                draftRepository.saveDraft(reconciled).fold(
                    onSuccess = { saved ->
                        updateState {
                            it.copy(
                                status = UiStatus.Content(saved),
                                currentStep = nextStep,
                                // Only ever meaningful transiently between the gear-search and
                                // gear-measurement steps; harmless to clear unconditionally elsewhere.
                                pendingGearTypeId = null,
                            )
                        }
                    },
                    onFailure = { emitLoadError(CatchRecordFlowEvent.SaveAndContinue(updatedDraft, nextStep), it) },
                )
            }
        }

        /**
         * Validates [gearTypeId] against the currently loaded reference data before recording it as the
         * pending gear-type selection: a gear type with no confirmed measurement schema
         * (`GearType.measurementFields` empty) must never be accepted, even via a direct event dispatch that
         * bypassed [uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear.GearTypeSearch.selectableGearTypes]'s
         * UI-level filtering (which already hides such gear types from the search screen's suggestions). An
         * invalid selection is silently ignored rather than surfaced as an error: the UI-level filtering is
         * the primary defence and already prevents a real user from ever reaching this with an invalid id.
         */
        private fun gearTypeSelected(gearTypeId: String) {
            val gearType = currentState.gearTypes.firstOrNull { it.id == gearTypeId } ?: return
            if (gearType.measurementFields.isEmpty()) return
            updateState { it.copy(pendingGearTypeId = gearTypeId) }
        }

        private fun gearMeasurementsSubmitted(measurements: Map<String, MeasurementValue>) {
            val draft = currentDraftOrNull() ?: return
            val gearTypeId = currentState.pendingGearTypeId ?: return
            val newGearUse =
                GearUse(
                    id = idFactory(),
                    gearTypeId = gearTypeId,
                    statisticalSubRectangleCode = null,
                    measurements = measurements,
                )
            val updatedDraft = draft.copy(gearUses = draft.gearUses + newGearUse)
            saveAndContinue(updatedDraft, WizardStep.GearSummary)
        }

        private fun speciesAddedToCurrentGear(speciesId: String) {
            val draft = currentDraftOrNull() ?: return
            val currentGearUse = nextGearUsePendingSpecies(draft) ?: return
            val alreadyAdded = currentGearUse.speciesWeights.any { it.speciesId == speciesId }
            val updatedGearUse =
                if (alreadyAdded) {
                    currentGearUse
                } else {
                    currentGearUse.copy(
                        speciesWeights =
                            currentGearUse.speciesWeights +
                                SpeciesWeightEntry(id = idFactory(), speciesId = speciesId),
                    )
                }
            val updatedDraft =
                draft.copy(gearUses = draft.gearUses.map { if (it.id == updatedGearUse.id) updatedGearUse else it })
            saveAndContinue(updatedDraft, WizardStep.GearSpeciesChecklist)
        }

        /**
         * Persists [updatedDraft] without advancing [WizardStep] — shared by the gear-summary checklist's
         * "Remove gear" action ([CatchRecordFlowEvent.GearRemoved]) and the species checklist's
         * "Remove a species" action ([CatchRecordFlowEvent.SpeciesRemoved]), both of which keep the user on
         * their current checklist screen after the bulk removal.
         */
        private fun persistDraftWithoutStepChange(
            event: CatchRecordFlowEvent,
            updatedDraft: CatchRecordDraft,
        ) {
            val previousDraft = currentDraftOrNull()
            updateState { it.copy(status = UiStatus.Loading) }
            launchInViewModelScope {
                val reconciled = DraftInvalidation.reconcile(previousDraft, updatedDraft)
                draftRepository.saveDraft(reconciled).fold(
                    onSuccess = { saved -> updateState { it.copy(status = UiStatus.Content(saved)) } },
                    onFailure = { emitLoadError(event, it) },
                )
            }
        }

        /**
         * Persists an edit made via a Check-your-answers "Change" link to an *already-completed* gear
         * (finding: "Completed gear measurement/stat/species must be editable through Change and back
         * flows"): [transform] updates the existing [GearUse] identified by [gearUseId] in place (never
         * appending a new one); FR10 dependent-data invalidation is applied by the same
         * [DraftInvalidation.reconcile] call every other persistence path goes through; [nextStep] is
         * [WizardStep.CheckYourAnswers] for a completed edit, or [WizardStep.GearSpeciesChecklist] for the
         * "add a species mid-edit" step (see [speciesAddedToGearUse]).
         */
        private fun persistGearEdit(
            event: CatchRecordFlowEvent,
            gearUseId: String,
            nextStep: WizardStep,
            transform: (GearUse) -> GearUse,
        ) {
            val draft = currentDraftOrNull() ?: return
            if (draft.gearUses.none { it.id == gearUseId }) return
            val updatedDraft =
                draft.copy(gearUses = draft.gearUses.map { if (it.id == gearUseId) transform(it) else it })
            updateState { it.copy(status = UiStatus.Loading) }
            launchInViewModelScope {
                val reconciled = DraftInvalidation.reconcile(draft, updatedDraft)
                draftRepository.saveDraft(reconciled).fold(
                    onSuccess = { saved ->
                        updateState { it.copy(status = UiStatus.Content(saved), currentStep = nextStep) }
                    },
                    onFailure = { emitLoadError(event, it) },
                )
            }
        }

        private fun editGearMeasurements(event: CatchRecordFlowEvent.EditGearMeasurements) {
            persistGearEdit(event, event.gearUseId, WizardStep.CheckYourAnswers) {
                it.copy(measurements = event.measurements)
            }
        }

        private fun editGearStatRectangle(event: CatchRecordFlowEvent.EditGearStatRectangle) {
            persistGearEdit(event, event.gearUseId, WizardStep.CheckYourAnswers) {
                it.copy(statisticalSubRectangleCode = event.statisticalSubRectangleCode)
            }
        }

        private fun editGearSpeciesWeights(event: CatchRecordFlowEvent.EditGearSpeciesWeights) {
            persistGearEdit(event, event.gearUseId, WizardStep.CheckYourAnswers) {
                it.copy(speciesWeights = event.speciesWeights)
            }
        }

        private fun speciesAddedToGearUse(event: CatchRecordFlowEvent.SpeciesAddedToGearUse) {
            val draft = currentDraftOrNull() ?: return
            val gearUse = draft.gearUses.firstOrNull { it.id == event.gearUseId } ?: return
            if (gearUse.speciesWeights.any { it.speciesId == event.speciesId }) {
                // Already added — a no-op re-navigation only, mirroring speciesAddedToCurrentGear.
                updateState { it.copy(currentStep = WizardStep.GearSpeciesChecklist) }
                return
            }
            persistGearEdit(event, event.gearUseId, WizardStep.GearSpeciesChecklist) {
                it.copy(
                    speciesWeights =
                        it.speciesWeights + SpeciesWeightEntry(id = idFactory(), speciesId = event.speciesId),
                )
            }
        }

        private fun markReadyToSubmit() {
            val draft = currentDraftOrNull() ?: return
            launchInViewModelScope {
                draftRepository.markReadyToSubmit(draft.id).fold(
                    onSuccess = { updated -> updateState { it.copy(status = UiStatus.Content(updated)) } },
                    onFailure = { emitLoadError(CatchRecordFlowEvent.MarkReadyToSubmit, it) },
                )
            }
        }

        /**
         * Phase 8 "Accept and submit trip details": attempts an online submission when connected, falling
         * back to the offline pending-sync path both when there is no connectivity at all *and* when an
         * online attempt itself fails (e.g. the stub/real backend call throws) — either way the user's
         * declaration has been accepted and the draft must not be lost, only queued — see ADR 0009.
         *
         * Validates the draft's completeness first (see [CatchRecordDraftValidation]) — an incomplete draft
         * must never be submitted, even if this were somehow reached with one (defence-in-depth against a
         * future bug or a corrupted/persisted state, not just the normal step-by-step wizard gating).
         */
        private fun acceptDeclarationAndSubmit() {
            val draft = currentDraftOrNull() ?: return
            val validation = CatchRecordDraftValidation.validateForSubmission(draft)
            if (validation is DraftSubmissionValidation.Invalid) {
                Timber.w("Blocked submission of an incomplete draft: %s", validation.issues.joinToString())
                updateState {
                    it.copy(status = UiStatus.Error(message = SUBMISSION_VALIDATION_MESSAGE, isRetryable = false))
                }
                return
            }
            updateState { it.copy(status = UiStatus.Loading) }
            launchInViewModelScope {
                if (connectivityChecker.isConnected()) {
                    submissionRepository.submit(draft).fold(
                        onSuccess = { completeSubmission(draft) },
                        onFailure = { enqueuePendingSync(draft) },
                    )
                } else {
                    enqueuePendingSync(draft)
                }
            }
        }

        private suspend fun completeSubmission(draft: CatchRecordDraft) {
            draftRepository.saveDraft(draft.copy(status = DraftStatus.Submitted)).fold(
                onSuccess = { saved ->
                    updateState {
                        it.copy(
                            status = UiStatus.Content(saved),
                            currentStep = WizardStep.SubmissionSuccess,
                        )
                    }
                },
                onFailure = { emitLoadError(CatchRecordFlowEvent.AcceptDeclarationAndSubmit, it) },
            )
        }

        private suspend fun enqueuePendingSync(draft: CatchRecordDraft) {
            draftRepository.saveDraft(draft.copy(status = DraftStatus.PendingSync)).fold(
                onSuccess = { saved ->
                    syncScheduler.scheduleSync(saved.id)
                    updateState {
                        it.copy(status = UiStatus.Content(saved), currentStep = WizardStep.SubmissionPendingSync)
                    }
                },
                onFailure = { emitLoadError(CatchRecordFlowEvent.AcceptDeclarationAndSubmit, it) },
            )
        }

        private fun emitLoadedDraft(
            draft: CatchRecordDraft,
            nextStep: WizardStep,
            previouslyUsedPorts: List<Port>,
        ) {
            updateState {
                it.copy(
                    status = UiStatus.Content(draft),
                    currentStep = nextStep,
                    previouslyUsedPorts = previouslyUsedPorts,
                    departurePortEntryMode = deriveDeparturePortEntryMode(previouslyUsedPorts),
                    samePortCandidate = previouslyUsedPorts.firstOrNull(),
                )
            }
        }

        private fun deriveDeparturePortEntryMode(previouslyUsedPorts: List<Port>): DeparturePortEntryMode =
            if (previouslyUsedPorts.isEmpty()) {
                DeparturePortEntryMode.Search
            } else {
                // First previously-used port is treated as the most likely candidate and offered once.
                DeparturePortEntryMode.SamePortShortcut
            }

        private fun currentDate(): DmyDate {
            val localDate = Instant.ofEpochMilli(clock()).atZone(ZoneOffset.UTC).toLocalDate()
            return DmyDate(localDate.dayOfMonth, localDate.monthValue, localDate.year)
        }

        private fun currentDraftOrNull(): CatchRecordDraft? =
            (currentState.status as? UiStatus.Content<CatchRecordDraft>)?.value

        /** Re-attempts [lastFailedEvent] (see [CatchRecordFlowEvent.Retry]'s doc comment); a no-op if none. */
        private fun retryLastFailedEvent() {
            val event = lastFailedEvent ?: return
            lastFailedEvent = null
            dispatch(event)
        }

        /**
         * Loads the offline statistical-sub-area map dataset (ADR 0013) into its own independent
         * [CatchRecordFlowViewState.mapGeometryStatus] slice (see that field's doc comment for why this is
         * deliberately not folded into [enterFlow]). Guards against redundant re-loads while already
         * [UiStatus.Loading] or once already [UiStatus.Content] loaded — but *does* allow a fresh attempt
         * from [UiStatus.Idle] (first load) or [UiStatus.Error] (explicit Retry), matching the same
         * "retry a real failure, don't restart a real success" semantics as [retryLastFailedEvent] elsewhere
         * in this ViewModel, without sharing its single-slot [lastFailedEvent]/[status]-only bookkeeping —
         * this event is dispatched directly by the map screen's own Retry control, not the shared one.
         */
        private fun loadMapGeometry() {
            when (currentState.mapGeometryStatus) {
                is UiStatus.Loading, is UiStatus.Content -> return
                UiStatus.Idle, is UiStatus.Error -> Unit
            }
            updateState { it.copy(mapGeometryStatus = UiStatus.Loading) }
            launchInViewModelScope {
                mapGeometryRepository.getMapGeometry().fold(
                    onSuccess = { dataset ->
                        updateState { it.copy(mapGeometryStatus = UiStatus.Content(dataset)) }
                    },
                    onFailure = { throwable ->
                        Timber.w(throwable, "CatchRecordFlowViewModel map geometry load failed")
                        val safeError = SafeErrorMapper.map(throwable)
                        updateState {
                            it.copy(
                                mapGeometryStatus =
                                    UiStatus.Error(message = safeError.message, isRetryable = safeError.isRetryable),
                            )
                        }
                    },
                )
            }
        }

        /**
         * Records [event] as retryable (see [retryLastFailedEvent]), logs [throwable] for diagnostics via
         * Timber (never the user-facing message — see ADR 0011), and maps it to a safe, generic,
         * already-reviewed user-facing message via [SafeErrorMapper] — **never** [Throwable.message]
         * directly, which may embed data unsafe to show a user or log verbatim.
         */
        private fun emitLoadError(
            event: CatchRecordFlowEvent,
            throwable: Throwable,
        ) {
            Timber.w(throwable, "CatchRecordFlowViewModel action failed: ${event::class.simpleName}")
            lastFailedEvent = event
            val safeError = SafeErrorMapper.map(throwable)
            updateState {
                it.copy(status = UiStatus.Error(message = safeError.message, isRetryable = safeError.isRetryable))
            }
        }
    }
