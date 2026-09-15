package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard

import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.core.architecture.ViewState
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.MeasurementValue
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Port
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Vessel

enum class DeparturePortEntryMode {
    SamePortShortcut,
    FavouriteList,
    Search,
}

/** Immutable UI state for [CatchRecordFlowViewModel], shared across every wizard screen in the graph. */
data class CatchRecordFlowViewState(
    val currentStep: WizardStep = WizardStep.VesselSelection,
    // Idle means the flow context loaded successfully but no persisted draft exists yet; the user must
    // choose a vessel before the first Room-backed draft is created.
    val status: UiStatus<CatchRecordDraft> = UiStatus.Idle,
    val vessels: List<Vessel> = emptyList(),
    val ports: List<Port> = emptyList(),
    val previouslyUsedPorts: List<Port> = emptyList(),
    val departurePortEntryMode: DeparturePortEntryMode = DeparturePortEntryMode.Search,
    val samePortCandidate: Port? = null,
    val gearTypes: List<GearType> = emptyList(),
    /**
     * The gear type chosen on the gear-search screen, held only transiently until the measurement screen
     * submits (at which point a real [uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse]
     * is created and this is cleared). Not itself persisted to Room — if the process dies mid-measurement
     * entry the user simply re-picks the gear type; no captured data is lost since nothing was saved yet.
     */
    val pendingGearTypeId: String? = null,
) : ViewState

/** UI-originated events for the catch-record wizard flow, dispatched by whichever step screen is shown. */
sealed interface CatchRecordFlowEvent {
    data object EnterFlow : CatchRecordFlowEvent

    /** Resume the loaded active draft at its last-reached step. */
    data object ResumeDraft : CatchRecordFlowEvent

    /** Permanently delete the loaded active draft and return the flow to vessel selection. */
    data object DeleteDraft : CatchRecordFlowEvent

    /** Start or resume the selected vessel's active draft, then continue to the trip-timing step. */
    data class VesselSelected(
        val vesselId: String,
    ) : CatchRecordFlowEvent

    /** Persist the trip-timing answer; selecting today also defaults both dates to the injected clock date. */
    data class TripTodayAnswered(
        val isTripToday: Boolean,
    ) : CatchRecordFlowEvent

    /** The user declined the same-port shortcut, so the departure-port step should show favourites/search. */
    data object SamePortShortcutDeclined : CatchRecordFlowEvent

    /** Persist the same-port shortcut choice and skip directly to the gear-search step. */
    data object SamePortShortcutAccepted : CatchRecordFlowEvent

    /**
     * Persist [updatedDraft] (the "save and continue" autosave) and advance to [nextStep]. Persistence
     * happens before the step advances, per ADR 0007, so at most the in-progress current-step fields are
     * ever lost to a process death mid-transition.
     */
    data class SaveAndContinue(
        val updatedDraft: CatchRecordDraft,
        val nextStep: WizardStep,
    ) : CatchRecordFlowEvent

    /** Records the gear type chosen on the gear-search screen, ready for the measurement step. */
    data class GearTypeSelected(
        val gearTypeId: String,
    ) : CatchRecordFlowEvent

    /**
     * Builds and appends a new [uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse] for
     * the pending gear type (see [CatchRecordFlowViewState.pendingGearTypeId]) with the entered
     * [measurements], persists it, and advances to the gear-summary checklist.
     */
    data class GearMeasurementsSubmitted(
        val measurements: Map<String, MeasurementValue>,
    ) : CatchRecordFlowEvent

    /**
     * Bulk-removes the checked gear(s) from the draft (see gear-summary checklist "Remove gear" action)
     * and persists immediately; does not advance [WizardStep] since the user stays on the checklist.
     */
    data class GearRemoved(
        val updatedDraft: CatchRecordDraft,
    ) : CatchRecordFlowEvent

    data object MarkReadyToSubmit : CatchRecordFlowEvent
}
