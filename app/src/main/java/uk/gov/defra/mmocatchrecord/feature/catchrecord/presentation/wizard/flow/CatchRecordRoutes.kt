package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow

import kotlinx.serialization.Serializable

/**
 * Type-safe Navigation Compose routes for every destination in the catch-record wizard's nested graph (see
 * ADR 0007/0010, and [uk.gov.defra.mmocatchrecord.common.navigation.CatchRecordGraphRoute] for the graph's
 * own route). [routeFor]/[editRouteFor] map a [WizardStep] to its route object.
 *
 * [GearMeasurementRoute], [GearStatRectangleRoute], [GearSpeciesSearchRoute] and [GearSpeciesChecklistRoute]
 * carry an optional [editGearUseId]: when non-null, the destination is editing an *already-completed* gear
 * use (via a check-your-answers "Change" link — see [editRouteFor]) rather than progressing the normal
 * forward per-gear loop.
 */
@Serializable
data object CatchRecordEntryRoute

@Serializable
data object DraftResumeRoute

@Serializable
data object VesselSelectionRoute

@Serializable
data object TripTodayRoute

@Serializable
data object DepartureDateRoute

@Serializable
data object ReturnDateRoute

@Serializable
data object DeparturePortRoute

@Serializable
data object ReturnPortRoute

@Serializable
data object GearSearchRoute

@Serializable
data class GearMeasurementRoute(
    val editGearUseId: String? = null,
)

@Serializable
data object GearSummaryRoute

@Serializable
data class GearStatRectangleRoute(
    val editGearUseId: String? = null,
)

@Serializable
data class GearSpeciesSearchRoute(
    val editGearUseId: String? = null,
)

@Serializable
data class GearSpeciesChecklistRoute(
    val editGearUseId: String? = null,
)

@Serializable
data object NotLandedStraightAwayDecisionRoute

@Serializable
data object NotLandedStraightAwaySpeciesRoute

/** Placeholder landing spot after the gear-summary checklist — see [PhaseThreeCompleteScreen]. */
@Serializable
data object PhaseThreeCompleteRoute

@Serializable
data object LateSubmissionWarningRoute

@Serializable
data object CheckYourAnswersRoute

@Serializable
data object SubmissionSuccessRoute

@Serializable
data object SubmissionPendingSyncRoute
