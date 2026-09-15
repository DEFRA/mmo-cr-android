@file:Suppress("detekt.TooManyFunctions", "detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard

import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import uk.gov.defra.mmocatchrecord.core.architecture.BaseViewModel
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraftRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DmyDate
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelection
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelectionMode
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
                CatchRecordFlowEvent.MarkReadyToSubmit -> markReadyToSubmit()
            }
        }

        private fun enterFlow() {
            updateState { it.copy(status = UiStatus.Loading) }
            launchInViewModelScope {
                runCatching {
                    val vessels = referenceDataRepository.getVessels().getOrThrow()
                    val ports = referenceDataRepository.getPorts().getOrThrow()
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
            saveAndContinue(updatedDraft, WizardStep.GearLoop)
        }

        private fun saveAndContinue(
            updatedDraft: CatchRecordDraft,
            nextStep: WizardStep,
        ) {
            updateState { it.copy(status = UiStatus.Loading) }
            launchInViewModelScope {
                draftRepository.saveDraft(updatedDraft).fold(
                    onSuccess = { saved ->
                        updateState { it.copy(status = UiStatus.Content(saved), currentStep = nextStep) }
                    },
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
