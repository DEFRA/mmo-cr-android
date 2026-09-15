// `dispatch`'s cyclomatic complexity grows by exactly one flat `when` branch per wizard event (each a
// one-line delegation to its own private handler, not nested logic) — same rationale as the existing
// `TooManyFunctions` suppression: it scales with the number of wizard steps/events, not with genuine
// per-branch complexity, so a file-level suppression is more honest than an arbitrary split.
@file:Suppress("detekt.TooManyFunctions", "detekt.MaxLineLength", "detekt.CyclomaticComplexMethod")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard

import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import uk.gov.defra.mmocatchrecord.core.architecture.BaseViewModel
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraftRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DmyDate
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.MeasurementValue
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelection
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelectionMode
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.SpeciesWeightEntry
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Port
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.ReferenceDataRepository
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject

/** Nav-graph-scoped ViewModel shared by every catch-record wizard screen. */
@HiltViewModel
@Suppress("TooManyFunctions")
class CatchRecordFlowViewModel
    @Inject
    constructor(
        private val draftRepository: CatchRecordDraftRepository,
        private val referenceDataRepository: ReferenceDataRepository,
        private val clock: () -> Long,
        private val idFactory: () -> String,
        defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    ) : BaseViewModel<CatchRecordFlowViewState, CatchRecordFlowEvent>(
            initialState = CatchRecordFlowViewState(),
            defaultDispatcher = defaultDispatcher,
        ) {
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
                is CatchRecordFlowEvent.GearRemoved -> persistDraftWithoutStepChange(event.updatedDraft)
                is CatchRecordFlowEvent.SpeciesAddedToCurrentGear -> speciesAddedToCurrentGear(event.speciesId)
                is CatchRecordFlowEvent.SpeciesRemoved -> persistDraftWithoutStepChange(event.updatedDraft)
                CatchRecordFlowEvent.MarkReadyToSubmit -> markReadyToSubmit()
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
                }.onFailure(::emitLoadError)
            }
        }

        private fun resumeDraft() {
            val draft = currentDraftOrNull() ?: return
            updateState { it.copy(currentStep = nextWizardStepForDraft(draft), status = UiStatus.Content(draft)) }
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
                    onFailure = ::emitLoadError,
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
                }.onFailure(::emitLoadError)
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
            updateState { it.copy(status = UiStatus.Loading) }
            launchInViewModelScope {
                draftRepository.saveDraft(updatedDraft).fold(
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
                    onFailure = ::emitLoadError,
                )
            }
        }

        private fun gearTypeSelected(gearTypeId: String) {
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
        private fun persistDraftWithoutStepChange(updatedDraft: CatchRecordDraft) {
            updateState { it.copy(status = UiStatus.Loading) }
            launchInViewModelScope {
                draftRepository.saveDraft(updatedDraft).fold(
                    onSuccess = { saved -> updateState { it.copy(status = UiStatus.Content(saved)) } },
                    onFailure = ::emitLoadError,
                )
            }
        }

        private fun markReadyToSubmit() {
            val draft = currentDraftOrNull() ?: return
            launchInViewModelScope {
                draftRepository.markReadyToSubmit(draft.id).fold(
                    onSuccess = { updated -> updateState { it.copy(status = UiStatus.Content(updated)) } },
                    onFailure = ::emitLoadError,
                )
            }
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

        private fun emitLoadError(error: Throwable) {
            updateState {
                it.copy(
                    status =
                        UiStatus.Error(
                            message = error.message ?: "Unable to load your catch record draft.",
                            isRetryable = true,
                        ),
                )
            }
        }
    }
