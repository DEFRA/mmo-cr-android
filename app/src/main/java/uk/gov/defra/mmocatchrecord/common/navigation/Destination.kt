package uk.gov.defra.mmocatchrecord.common.navigation

/**
 * Type-safe navigation destinations for Navigation Compose.
 *
 * Stage 1 wires only placeholder destinations for the three [uk.gov.defra.mmocatchrecord.core.root.RootPhase]
 * states; real feature destinations are added in later stages.
 */
sealed class Destination(
    val route: String,
) {
    data object Splash : Destination("splash")

    data object SignIn : Destination("sign_in")

    data object AppLock : Destination("app_lock")

    data object Home : Destination("home")

    /** Route constants for the nested catch-record wizard graph (see ADR 0007). */
    data object CatchRecordFlow {
        const val GRAPH_ROUTE = "catch_record_flow"
        const val ENTRY_ROUTE = "$GRAPH_ROUTE/entry"
        const val DRAFT_RESUME_ROUTE = "$GRAPH_ROUTE/draft_resume"
        const val VESSEL_SELECTION_ROUTE = "$GRAPH_ROUTE/vessel_selection"
        const val TRIP_TODAY_ROUTE = "$GRAPH_ROUTE/trip_today"
        const val DEPARTURE_DATE_ROUTE = "$GRAPH_ROUTE/departure_date"
        const val RETURN_DATE_ROUTE = "$GRAPH_ROUTE/return_date"
        const val DEPARTURE_PORT_ROUTE = "$GRAPH_ROUTE/departure_port"
        const val RETURN_PORT_ROUTE = "$GRAPH_ROUTE/return_port"
        const val GEAR_SEARCH_ROUTE = "$GRAPH_ROUTE/gear_search"
        const val GEAR_MEASUREMENT_ROUTE = "$GRAPH_ROUTE/gear_measurement"
        const val GEAR_SUMMARY_ROUTE = "$GRAPH_ROUTE/gear_summary"
        const val GEAR_STAT_RECTANGLE_ROUTE = "$GRAPH_ROUTE/gear_stat_rectangle"
        const val GEAR_SPECIES_SEARCH_ROUTE = "$GRAPH_ROUTE/gear_species_search"
        const val GEAR_SPECIES_CHECKLIST_ROUTE = "$GRAPH_ROUTE/gear_species_checklist"
        const val NOT_LANDED_STRAIGHT_AWAY_DECISION_ROUTE = "$GRAPH_ROUTE/not_landed_straight_away_decision"
        const val NOT_LANDED_STRAIGHT_AWAY_SPECIES_ROUTE = "$GRAPH_ROUTE/not_landed_straight_away_species"

        /**
         * Placeholder landing spot after the gear-summary checklist's "Save and continue", until the
         * Phase 4 statistical sub-rectangle screen (and beyond) are built — see [PhaseThreeCompleteScreen].
         */
        const val PHASE_THREE_COMPLETE_ROUTE = "$GRAPH_ROUTE/phase_three_complete"
    }
}
