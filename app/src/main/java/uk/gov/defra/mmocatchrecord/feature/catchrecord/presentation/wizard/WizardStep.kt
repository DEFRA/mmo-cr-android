// `routeFor` is a flat one-branch-per-`WizardStep` route lookup, and `nextWizardStepForDraft` is this
// wizard's step-derivation state machine — both necessarily grow one `when` branch per wizard step, not
// per genuine branch-level logic complexity, so a file-level suppression matches the same rationale used
// for `CatchRecordFlowViewModel`'s `dispatch` (see that file's `@file:Suppress` comment).
@file:Suppress("detekt.CyclomaticComplexMethod")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard

import uk.gov.defra.mmocatchrecord.common.navigation.Destination
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse

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
     * "What gear did you use?" — autocomplete search over
     * [uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearType].
     */
    data object GearSearch : WizardStep

    /**
     * Generic, schema-driven "Enter the measurements for {gear}" screen: fields shown are resolved from
     * the selected gear type's `measurementFields` schema (see ADR 0007 update / Phase 3), not hardcoded
     * per gear type.
     */
    data object GearMeasurement : WizardStep

    /** Checklist of every gear added so far, with confirm/remove/add-another actions. */
    data object GearSummary : WizardStep

    /**
     * Per-confirmed-gear "Where was the majority of your catch caught using {gear}?" statistical
     * sub-rectangle selection (Phase 4) — looped once for each
     * [uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse] with `confirmedUsedOnTrip ==
     * true`, in `draft.gearUses` order, until every confirmed gear has recorded a
     * `statisticalSubRectangleCode`. A single step (not one per gear or per sub-screen): which gear is
     * "current" and whether the grid/radio-list/autocomplete sub-screen is shown is derived from the draft
     * and local screen state respectively — see `GearStatRectangleScreen` and [nextWizardStepForDraft].
     */
    data object GearStatRectangle : WizardStep

    /**
     * Per-confirmed-gear "Which species did you catch with {gear}?" search screen (Phase 5A, screen 1) —
     * looped alongside [GearStatRectangle]: for each confirmed gear use, its stat-rectangle step must
     * complete before its species step begins (interleaved per gear, not "all stat-rectangles then all
     * species") — see [nextGearUsePendingSpecies] and [nextWizardStepForDraft].
     */
    data object GearSpeciesSearch : WizardStep

    /**
     * Per-confirmed-gear species checklist + progressive-disclosure weight capture (Phase 5A, screen 2):
     * "Weight above minimum size retained (kg)" (mandatory-ness varies per species), plus optional
     * "below minimum size"/"legally discarded" weight fields revealed via add/remove links.
     */
    data object GearSpeciesChecklist : WizardStep

    /**
     * Phase 5B, screen 3 — asked once, after every confirmed gear's species/weights step is complete:
     * "Is there any catch from this trip that you will not be landing straight away?"
     */
    data object NotLandedStraightAwayDecision : WizardStep

    /**
     * Phase 5B, screen 4 — shown only when [WizardStep.NotLandedStraightAwayDecision] was answered `true`:
     * a checklist of every distinct species captured anywhere in the draft, with a single "Weight above
     * minimum size kept onboard or in keep pots (kg)" field per checked species.
     */
    data object NotLandedStraightAwaySpecies : WizardStep

    /** TODO(Phase 6): landing/storage entry, fields TBD, screenshots pending. */
    data object LandingStorage : WizardStep

    /** TODO(Phase 6+): review and submit screen. */
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
        WizardStep.GearSearch -> Destination.CatchRecordFlow.GEAR_SEARCH_ROUTE
        WizardStep.GearMeasurement -> Destination.CatchRecordFlow.GEAR_MEASUREMENT_ROUTE
        WizardStep.GearSummary -> Destination.CatchRecordFlow.GEAR_SUMMARY_ROUTE
        WizardStep.GearStatRectangle -> Destination.CatchRecordFlow.GEAR_STAT_RECTANGLE_ROUTE
        WizardStep.GearSpeciesSearch -> Destination.CatchRecordFlow.GEAR_SPECIES_SEARCH_ROUTE
        WizardStep.GearSpeciesChecklist -> Destination.CatchRecordFlow.GEAR_SPECIES_CHECKLIST_ROUTE
        WizardStep.NotLandedStraightAwayDecision -> Destination.CatchRecordFlow.NOT_LANDED_STRAIGHT_AWAY_DECISION_ROUTE
        WizardStep.NotLandedStraightAwaySpecies -> Destination.CatchRecordFlow.NOT_LANDED_STRAIGHT_AWAY_SPECIES_ROUTE
        WizardStep.LandingStorage -> Destination.CatchRecordFlow.PHASE_THREE_COMPLETE_ROUTE
        WizardStep.ReviewAndSubmit -> Destination.CatchRecordFlow.PHASE_THREE_COMPLETE_ROUTE
    }

private fun isStatRectanglePending(gearUse: GearUse): Boolean = gearUse.statisticalSubRectangleCode == null

private fun isSpeciesPending(gearUse: GearUse): Boolean = gearUse.speciesWeights.none { it.confirmedCaught }

/**
 * The first confirmed gear use (in `draft.gearUses` order) that still has *either* its stat-rectangle *or*
 * its species step outstanding. Used to interleave the two per-gear loops correctly: gear N's
 * stat-rectangle/species steps are both fully resolved before gear N+1's stat-rectangle step is ever
 * reached, even though the two loops are driven by two independently-named helpers below.
 */
private fun firstIncompleteConfirmedGearUse(draft: CatchRecordDraft): GearUse? =
    draft.gearUses.firstOrNull { it.confirmedUsedOnTrip && (isStatRectanglePending(it) || isSpeciesPending(it)) }

/**
 * The next confirmed gear use (in `draft.gearUses` order) still awaiting a statistical sub-rectangle, or
 * `null` once every confirmed gear has one recorded — see [WizardStep.GearStatRectangle].
 */
fun nextGearUsePendingStatRectangle(draft: CatchRecordDraft): GearUse? =
    firstIncompleteConfirmedGearUse(draft)?.takeIf(::isStatRectanglePending)

/**
 * The next confirmed gear use (in `draft.gearUses` order, and only once its own stat-rectangle is already
 * recorded) still awaiting a confirmed species catch, or `null` once every confirmed gear has at least one
 * — see [WizardStep.GearSpeciesSearch]/[WizardStep.GearSpeciesChecklist].
 */
fun nextGearUsePendingSpecies(draft: CatchRecordDraft): GearUse? =
    firstIncompleteConfirmedGearUse(draft)?.takeIf { !isStatRectanglePending(it) && isSpeciesPending(it) }

fun nextWizardStepForDraft(draft: CatchRecordDraft): WizardStep =
    when {
        draft.isTripToday == null -> WizardStep.TripToday
        draft.isTripToday == false && draft.departureDate == null -> WizardStep.DepartureDate
        draft.isTripToday == false && draft.returnDate == null -> WizardStep.ReturnDate
        draft.departurePort == null -> WizardStep.DeparturePort
        draft.returnPort == null -> WizardStep.ReturnPort
        draft.gearUses.isEmpty() -> WizardStep.GearSearch
        nextGearUsePendingStatRectangle(draft) != null -> WizardStep.GearStatRectangle
        nextGearUsePendingSpecies(draft) != null ->
            if (nextGearUsePendingSpecies(draft)?.speciesWeights.isNullOrEmpty()) {
                WizardStep.GearSpeciesSearch
            } else {
                WizardStep.GearSpeciesChecklist
            }
        draft.gearUses.any { it.confirmedUsedOnTrip } && draft.notLandedStraightAway == null ->
            WizardStep.NotLandedStraightAwayDecision
        draft.notLandedStraightAway == true && draft.notLandedSpeciesEntries.isEmpty() ->
            WizardStep.NotLandedStraightAwaySpecies
        draft.gearUses.any { it.confirmedUsedOnTrip } -> WizardStep.LandingStorage
        else -> WizardStep.GearSummary
    }
