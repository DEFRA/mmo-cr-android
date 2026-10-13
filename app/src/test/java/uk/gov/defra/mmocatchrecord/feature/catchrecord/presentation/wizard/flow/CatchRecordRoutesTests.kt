package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers [DraftResumeRoute]'s `draftId` default (only exercised when a caller omits the argument),
 * [routeFor]/[editRouteFor] for every [WizardStep] (constructing every route object declared in
 * `CatchRecordRoutes.kt`), and the four `editGearUseId`-carrying route data classes' equality/copy.
 */
class CatchRecordRoutesTests {
    private val allSteps =
        listOf(
            WizardStep.DraftResume,
            WizardStep.VesselSelection,
            WizardStep.TripToday,
            WizardStep.DepartureDate,
            WizardStep.ReturnDate,
            WizardStep.DeparturePort,
            WizardStep.ReturnPort,
            WizardStep.GearSearch,
            WizardStep.GearMeasurement,
            WizardStep.GearSummary,
            WizardStep.GearStatRectangle,
            WizardStep.GearSpeciesSearch,
            WizardStep.GearSpeciesChecklist,
            WizardStep.NotLandedStraightAwayDecision,
            WizardStep.NotLandedStraightAwaySpecies,
            WizardStep.LandingStorage,
            WizardStep.LateSubmissionWarning,
            WizardStep.CheckYourAnswers,
            WizardStep.SubmissionSuccess,
            WizardStep.SubmissionPendingSync,
        )

    @Test
    fun `draft resume route defaults draftId to null when omitted`() {
        assertNull(DraftResumeRoute().draftId)
    }

    @Test
    fun `routeFor constructs a route for every wizard step`() {
        allSteps.forEach { step -> assertNotNull(routeFor(step)) }
    }

    @Test
    fun `editRouteFor carries the edit gear use id for the three editable gear steps`() {
        assertEquals(GearMeasurementRoute(editGearUseId = "gear-1"), editRouteFor(WizardStep.GearMeasurement, "gear-1"))
        assertEquals(
            GearStatRectangleRoute(editGearUseId = "gear-1"),
            editRouteFor(WizardStep.GearStatRectangle, "gear-1"),
        )
        assertEquals(
            GearSpeciesChecklistRoute(editGearUseId = "gear-1"),
            editRouteFor(WizardStep.GearSpeciesChecklist, "gear-1"),
        )
    }

    @Test
    fun `editRouteFor falls back to routeFor for every other wizard step`() {
        allSteps
            .filterNot {
                it in
                    setOf(
                        WizardStep.GearMeasurement,
                        WizardStep.GearStatRectangle,
                        WizardStep.GearSpeciesChecklist,
                    )
            }.forEach { step -> assertEquals(routeFor(step), editRouteFor(step, "gear-1")) }
    }

    @Test
    fun `gear route data classes support equality, copy and toString`() {
        val measurement = GearMeasurementRoute(editGearUseId = "a")
        assertEquals(measurement, measurement.copy())
        assertNotNull(measurement.toString())

        val statRectangle = GearStatRectangleRoute(editGearUseId = "a")
        assertEquals(statRectangle, statRectangle.copy())
        assertNotNull(statRectangle.toString())

        val speciesSearch = GearSpeciesSearchRoute(editGearUseId = "a")
        assertEquals(speciesSearch, speciesSearch.copy())
        assertNotNull(speciesSearch.toString())

        val speciesChecklist = GearSpeciesChecklistRoute(editGearUseId = "a")
        assertEquals(speciesChecklist, speciesChecklist.copy())
        assertNotNull(speciesChecklist.toString())
    }

    /** Navigation Compose serializes typed route args via kotlinx.serialization at runtime - this
     * round-trip exercises each route's generated serialization constructor (distinct from its
     * regular/default constructors, and otherwise only reachable through real nav-graph traversal). */
    @Test
    fun `gear route data classes round trip through kotlinx serialization`() {
        val measurement = GearMeasurementRoute(editGearUseId = "a")
        assertEquals(measurement, Json.decodeFromString<GearMeasurementRoute>(Json.encodeToString(measurement)))

        val statRectangle = GearStatRectangleRoute(editGearUseId = "a")
        assertEquals(statRectangle, Json.decodeFromString<GearStatRectangleRoute>(Json.encodeToString(statRectangle)))

        val speciesSearch = GearSpeciesSearchRoute(editGearUseId = "a")
        assertEquals(speciesSearch, Json.decodeFromString<GearSpeciesSearchRoute>(Json.encodeToString(speciesSearch)))

        val speciesChecklist = GearSpeciesChecklistRoute(editGearUseId = "a")
        assertEquals(
            speciesChecklist,
            Json.decodeFromString<GearSpeciesChecklistRoute>(Json.encodeToString(speciesChecklist)),
        )
    }

    /** Decoding a JSON object that omits `editGearUseId` exercises each route's generated serialization
     * constructor's "field not present, fall back to its default" branch - distinct from decoding a JSON
     * where the field was explicitly present, covered above. */
    @Test
    fun `gear route data classes default editGearUseId to null when absent from the decoded json`() {
        assertNull(Json.decodeFromString<GearMeasurementRoute>("{}").editGearUseId)
        assertNull(Json.decodeFromString<GearStatRectangleRoute>("{}").editGearUseId)
        assertNull(Json.decodeFromString<GearSpeciesSearchRoute>("{}").editGearUseId)
        assertNull(Json.decodeFromString<GearSpeciesChecklistRoute>("{}").editGearUseId)
    }
}
