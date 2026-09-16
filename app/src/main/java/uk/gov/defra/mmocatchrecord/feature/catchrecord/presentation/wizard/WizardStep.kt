// `routeFor` is a flat one-branch-per-`WizardStep` route lookup, and `nextWizardStepForDraft` is this
// wizard's step-derivation state machine — both necessarily grow one `when` branch per wizard step, not
// per genuine branch-level logic complexity, so a file-level suppression matches the same rationale used
// for `CatchRecordFlowViewModel`'s `dispatch` (see that file's `@file:Suppress` comment).
@file:Suppress("detekt.CyclomaticComplexMethod")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard

import androidx.navigation.NavOptionsBuilder
import uk.gov.defra.mmocatchrecord.common.navigation.Destination
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse

/**
 * A step in the "Create a Catch Record" wizard. Confirmed Stage-1 steps are modeled fully; steps for
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

    /** TODO(Phase 6): landing/storage entry, fields TBD, screenshots pending — not yet built/routed to. */
    data object LandingStorage : WizardStep

    /**
     * Phase 8, screen 1 (conditional): "This catch record is being submitted X days after the trip end
     * date" — shown only when [nextWizardStepForDraft] determines the record is being submitted more than
     * 24 hours after [uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft.returnDate]
     * — see [LateSubmissionSupport].
     */
    data object LateSubmissionWarning : WizardStep

    /**
     * Phase 8, screen 2 — the GDS "check your answers" review screen: grouped Trip/Gear used/Species
     * caught/Species not landed sections (see [CheckYourAnswersSupport]), each row deep-linking back to
     * the wizard step where it was captured, followed by the declaration and "Accept and submit trip
     * details" action.
     *
     * TODO(Phase 6/7): once landing-storage capture and dependent-data invalidation exist, insert their
     * steps *before* this one in [nextWizardStepForDraft], and add a "Landing storage" section to
     * [CheckYourAnswersSupport.buildSections].
     */
    data object CheckYourAnswers : WizardStep

    /** Phase 8, screen 3: online submission succeeded — draft status becomes [DraftStatus.Submitted]. */
    data object SubmissionSuccess : WizardStep

    /** Phase 8, screen 4: submitted while offline (or the online attempt failed) — queued for background sync. */
    data object SubmissionPendingSync : WizardStep
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
        WizardStep.LateSubmissionWarning -> Destination.CatchRecordFlow.LATE_SUBMISSION_WARNING_ROUTE
        WizardStep.CheckYourAnswers -> Destination.CatchRecordFlow.CHECK_YOUR_ANSWERS_ROUTE
        WizardStep.SubmissionSuccess -> Destination.CatchRecordFlow.SUBMISSION_SUCCESS_ROUTE
        WizardStep.SubmissionPendingSync -> Destination.CatchRecordFlow.SUBMISSION_PENDING_SYNC_ROUTE
    }

/**
 * Extra [androidx.navigation.NavOptionsBuilder] configuration required specifically when navigating away
 * from [WizardStep.CheckYourAnswers] to one of the two Phase 8 submission-result steps (see
 * `CheckYourAnswersScreen`'s post-submit `LaunchedEffect`, which drives that navigation once
 * `CatchRecordFlowViewModel.acceptDeclarationAndSubmit` has asynchronously resolved the outcome). Submission
 * is terminal for the draft, so every wizard screen pushed since entering the nav graph is cleared — down
 * to, but keeping, the graph's own back stack entry (so the nav-graph-scoped `CatchRecordFlowViewModel`
 * instance, and its already-updated post-submission draft state, survives) — meaning the user cannot
 * navigate back into check-your-answers or any earlier wizard screen for an already-submitted or
 * already-queued draft. A no-op for every other [WizardStep] (e.g. a "Change" link's synchronous
 * navigation back to an earlier step, which flows through the same `onNavigate` callback).
 *
 * A single, shared function (rather than duplicated inline logic) so `MmoNavHost`'s real wiring and any
 * instrumented test exercising this exact navigation behaviour cannot silently drift apart.
 */
fun NavOptionsBuilder.applySubmissionResultNavOptions(step: WizardStep) {
    if (step == WizardStep.SubmissionSuccess || step == WizardStep.SubmissionPendingSync) {
        popUpTo(Destination.CatchRecordFlow.GRAPH_ROUTE) { inclusive = false }
    }
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

fun nextWizardStepForDraft(
    draft: CatchRecordDraft,
    nowEpochMillis: Long = System.currentTimeMillis(),
): WizardStep =
    when {
        draft.isTripToday == null -> WizardStep.TripToday
        !draft.isTripToday && draft.departureDate == null -> WizardStep.DepartureDate
        !draft.isTripToday && draft.returnDate == null -> WizardStep.ReturnDate
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
        draft.gearUses.any { it.confirmedUsedOnTrip } -> resolveSubmissionStep(draft, nowEpochMillis)
        else -> WizardStep.GearSummary
    }

/**
 * The terminal step once every earlier wizard step is complete for [draft]: the Phase 8 submission-result
 * screen if [draft] already has an outcome, the (conditional) late-submission warning if it hasn't yet been
 * acknowledged and applies, otherwise the check-your-answers review screen.
 *
 * TODO(Phase 6/7): once landing-storage capture and dependent-data invalidation are built, their steps
 * belong *before* this resolver is reached (i.e. inserted into [nextWizardStepForDraft] ahead of the two
 * call sites below), not inside it.
 *
 * Exposed (not `private`) because [GearSummaryScreen] also calls it directly for its own "no confirmed gear
 * left" edge case — see that screen's doc comment.
 */
fun resolveSubmissionStep(
    draft: CatchRecordDraft,
    nowEpochMillis: Long = System.currentTimeMillis(),
): WizardStep =
    when {
        draft.status == DraftStatus.Submitted -> WizardStep.SubmissionSuccess
        draft.status == DraftStatus.PendingSync -> WizardStep.SubmissionPendingSync
        !draft.lateSubmissionWarningAcknowledged &&
            LateSubmissionSupport.isLateSubmission(draft.returnDate, nowEpochMillis) ->
            WizardStep.LateSubmissionWarning
        else -> WizardStep.CheckYourAnswers
    }
