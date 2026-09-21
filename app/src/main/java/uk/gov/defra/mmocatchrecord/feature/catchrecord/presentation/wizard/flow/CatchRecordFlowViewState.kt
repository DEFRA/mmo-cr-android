package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow

import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.core.architecture.ViewState
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.MeasurementValue
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.SpeciesWeightEntry
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Port
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Species
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.StatisticalSubRectangle
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Vessel
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear.GearStatRectangleSupport

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
     * The full, global set of statistical sub-rectangles (Phase 4), loaded once alongside ports/gear
     * types. "Nearby" rectangles for the grid/radio-list screens are derived client-side by filtering this
     * list on the departure port's [uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Port.statisticalAreaId]
     * (see [GearStatRectangleSupport.nearbyRectanglesFor]); the unfiltered list backs the "Other" free-text
     * autocomplete search across every known code.
     */
    val statisticalSubRectangles: List<StatisticalSubRectangle> = emptyList(),
    /** Every known species (Phase 5), loaded once alongside ports/gear types. */
    val species: List<Species> = emptyList(),
    /**
     * The gear type chosen on the gear-search screen, held only transiently until the measurement screen
     * submits (at which point a real [uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse]
     * is created and this is cleared). Not itself persisted to Room — if the process dies mid-measurement
     * entry the user simply re-picks the gear type; no captured data is lost since nothing was saved yet.
     */
    val pendingGearTypeId: String? = null,
) : ViewState

/**
 * The user-facing catch-record reference (see [CatchRecordDraft.catchRecordReference]), shown as a caption
 * on every wizard screen once the draft exists (see [uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordWizardScaffold]'s
 * `referenceNumber` parameter) — `null` before a draft has been created (e.g. vessel selection).
 */
val CatchRecordFlowViewState.catchRecordReference: String?
    get() = (status as? UiStatus.Content<CatchRecordDraft>)?.value?.catchRecordReference

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

    /**
     * Appends a new, unconfirmed
     * [uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.SpeciesWeightEntry] for [speciesId] to
     * the current gear use (see [nextGearUsePendingSpecies]), persists it, and advances to the
     * gear-species checklist. A no-op re-navigation (no duplicate entry) if [speciesId] was already added
     * to this gear.
     */
    data class SpeciesAddedToCurrentGear(
        val speciesId: String,
    ) : CatchRecordFlowEvent

    /**
     * Bulk-removes the checked species from the current gear's species checklist and persists immediately;
     * does not advance [WizardStep] since the user stays on the checklist — mirrors [GearRemoved].
     */
    data class SpeciesRemoved(
        val updatedDraft: CatchRecordDraft,
    ) : CatchRecordFlowEvent

    data object MarkReadyToSubmit : CatchRecordFlowEvent

    /**
     * Phase 8: the user accepted the check-your-answers declaration and tapped "Accept and submit trip
     * details" — see [CatchRecordFlowViewModel]'s online/offline branching logic. No payload: the draft to
     * submit is always the current [UiStatus.Content] draft already held in state.
     */
    data object AcceptDeclarationAndSubmit : CatchRecordFlowEvent

    /**
     * Re-attempts whichever event most recently failed (tracked internally by the ViewModel) — dispatched
     * by the shared retry control on any retryable [UiStatus.Error] (see `WizardErrorState`/finding on
     * "Retryable wizard errors need accessible Retry control"). A no-op if nothing has failed.
     */
    data object Retry : CatchRecordFlowEvent

    /**
     * Check-your-answers "Change" edit flow (finding: "Completed gear measurement/stat/species must be
     * editable through Change and back flows"): updates the *existing* [uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse]
     * identified by [gearUseId]'s measurements in place, then returns to [uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.WizardStep.CheckYourAnswers].
     */
    data class EditGearMeasurements(
        val gearUseId: String,
        val measurements: Map<String, MeasurementValue>,
    ) : CatchRecordFlowEvent

    /**
     * Check-your-answers "Change" edit flow for a gear's statistical sub-rectangle: updates the existing
     * gear use in place. FR10 dependent-data invalidation (clearing its species/weight entries, captured
     * against the previous rectangle) is applied centrally by the ViewModel's persistence funnel, not here.
     */
    data class EditGearStatRectangle(
        val gearUseId: String,
        val statisticalSubRectangleCode: String,
    ) : CatchRecordFlowEvent

    /**
     * Adds a new species to an *already-confirmed* gear use during a Check-your-answers edit (mirrors
     * [SpeciesAddedToCurrentGear], but targets an explicit [gearUseId] rather than "whichever gear is
     * next pending species" — the latter would resolve to nothing for a gear that already has confirmed
     * species). Advances to the gear-species checklist (still in this gear's edit context) rather than
     * check-your-answers, so the user can enter the new species' weight before returning.
     */
    data class SpeciesAddedToGearUse(
        val gearUseId: String,
        val speciesId: String,
    ) : CatchRecordFlowEvent

    /**
     * Check-your-answers "Change" edit flow for a gear's species/weight entries: replaces the existing gear
     * use's species-weight list wholesale (the species checklist screen's own save action already produces
     * the complete, validated list), then returns to check-your-answers.
     */
    data class EditGearSpeciesWeights(
        val gearUseId: String,
        val speciesWeights: List<SpeciesWeightEntry>,
    ) : CatchRecordFlowEvent
}
