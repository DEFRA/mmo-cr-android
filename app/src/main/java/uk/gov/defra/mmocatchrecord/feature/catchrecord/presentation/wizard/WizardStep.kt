package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard

import uk.gov.defra.mmocatchrecord.common.navigation.Destination
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft

/**
 * A step in the "Create a Catch Record" wizard. Confirmed Stage-1 steps are modelled fully; steps for
 * later phases (gear/measurement/species/landing-storage/review, blocked on user screenshots) are
 * deliberately placeholder-only entries so the flow's shape doesn't need re-plumbing once they're built —
 * see ADR 0007.
 */
sealed interface WizardStep {
    /** Resuming (or discarding) a previously started active draft, shown only if one exists on entry. */
    data object DraftResume : WizardStep

    data object VesselSelection : WizardStep

    data object TripToday : WizardStep

    data object DepartureDate : WizardStep

    data object ReturnDate : WizardStep

    data object DeparturePort : WizardStep

    data object ReturnPort : WizardStep

    /**
     * The per-gear loop: add one or more gear uses, each with its own measurements, stat-rectangle
     * selection and species/weight entries. TODO(Phase 3/4): replace with the real per-step screens once
     * gear/measurement/species/stat-area screenshots are confirmed.
     */
    data object GearLoop : WizardStep

    /** TODO(Phase 5): landing/storage entry screen, fields TBD. */
    data object LandingStorage : WizardStep

    /** TODO(Phase 6): review and submit screen. */
    data object ReviewAndSubmit : WizardStep
}

fun routeFor(step: WizardStep): String =
    when (step) {
        WizardStep.DraftResume -> Destination.CatchRecordFlow.DRAFT_RESUME_ROUTE
        WizardStep.VesselSelection -> Destination.CatchRecordFlow.VESSEL_SELECTION_ROUTE
        WizardStep.TripToday -> Destination.CatchRecordFlow.TRIP_TODAY_ROUTE
        WizardStep.DepartureDate -> Destination.CatchRecordFlow.DEPARTURE_DATE_ROUTE
        WizardStep.ReturnDate -> Destination.CatchRecordFlow.RETURN_DATE_ROUTE
        WizardStep.DeparturePort -> Destination.CatchRecordFlow.DEPARTURE_PORT_ROUTE
        WizardStep.ReturnPort -> Destination.CatchRecordFlow.RETURN_PORT_ROUTE
        WizardStep.GearLoop -> Destination.CatchRecordFlow.GEAR_LOOP_ROUTE
        WizardStep.LandingStorage -> Destination.CatchRecordFlow.GEAR_LOOP_ROUTE
        WizardStep.ReviewAndSubmit -> Destination.CatchRecordFlow.GEAR_LOOP_ROUTE
    }

fun nextWizardStepForDraft(draft: CatchRecordDraft): WizardStep =
    when {
        draft.isTripToday == null -> WizardStep.TripToday
        draft.isTripToday == false && draft.departureDate == null -> WizardStep.DepartureDate
        draft.isTripToday == false && draft.returnDate == null -> WizardStep.ReturnDate
        draft.departurePort == null -> WizardStep.DeparturePort
        draft.returnPort == null -> WizardStep.ReturnPort
        else -> WizardStep.GearLoop
    }
