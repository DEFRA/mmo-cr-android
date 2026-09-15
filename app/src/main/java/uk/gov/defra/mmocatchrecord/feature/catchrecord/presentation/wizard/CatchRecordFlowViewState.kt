package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard

import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.core.architecture.ViewState
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
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

    /** Persist the same-port shortcut choice and skip directly to the later-phase placeholder. */
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

    data object MarkReadyToSubmit : CatchRecordFlowEvent
}
