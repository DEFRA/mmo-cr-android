package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear

import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.MeasurementValue
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.MapGeometryDataset
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.StatisticalSubRectangleGeometry
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldKeys
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Port
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.StatisticalSubRectangle

/**
 * Pure (no Android/Compose dependency) helpers for the Phase 4 per-gear statistical sub-rectangle step —
 * see `GearStatRectangleScreen`.
 */
object GearStatRectangleSupport {
    /**
     * The `%1$s` argument plugged into the "Where was the majority of your catch caught using %1$s?" /
     * "Select the statistical sub area where the majority of your catch was caught using %1$s?" title
     * templates: the gear's lowercase title name, plus a "(mesh size Xmm)" qualifier when it has a
     * confirmed "identifying measurement" for its gear type. Only mesh size is confirmed as an identifying
     * measurement so far (per the Seine nets screenshot) — gear types with no mesh-size field (e.g.
     * Handlines) omit the parenthetical entirely, per the confirmed default. Deliberately plain,
     * unlocalised text, consistent with gear/port reference-data names elsewhere in this package.
     */
    fun gearNameWithIdentifyingMeasurementFor(
        gearType: GearType?,
        gearUse: GearUse,
    ): String {
        val gearName = gearType?.let(GearMeasurementSupport::titleGearNameFor) ?: gearUse.gearTypeId
        val meshSizeMm =
            (gearUse.measurements[GearMeasurementFieldKeys.MESH_SIZE_MM] as? MeasurementValue.Numeric)?.value
                ?: return gearName
        return "$gearName (mesh size ${GearMeasurementSupport.formatNumber(meshSizeMm)}mm)"
    }

    /**
     * Statistical sub-rectangles nearest [departurePort]'s statistical area (i.e. sharing its
     * [Port.statisticalAreaId]), or empty if the port or its area is unknown. Feeds the nearby map (screen
     * 1, via [nearbyGeometryFor]) and the global map+list (screen 2, reached via "Other"); the autocomplete
     * search (screen 3) instead searches [allRectangles] unfiltered.
     */
    fun nearbyRectanglesFor(
        departurePort: Port?,
        allRectangles: List<StatisticalSubRectangle>,
    ): List<StatisticalSubRectangle> =
        departurePort
            ?.let { port -> allRectangles.filter { it.statisticalAreaId == port.statisticalAreaId } }
            .orEmpty()

    /**
     * Joins the nearby reference-data [StatisticalSubRectangle.code]s onto the loaded map geometry
     * ([MapGeometryDataset.subRectangles]), restricted to sea-overlapping geometry only — a wholly-landlocked
     * or unmapped nearby code cannot be a real, tappable catch location, so it is simply omitted from the
     * nearby map's selectable set (it remains reachable via the "Other" -> global search fallback). Feeds
     * the nearby map screen (see `GearStatRectangleNearbyMapContent`/`AccessibleStatisticalAreaMap`).
     */
    fun nearbyGeometryFor(
        nearbyCodes: List<String>,
        dataset: MapGeometryDataset,
    ): List<StatisticalSubRectangleGeometry> {
        val codes = nearbyCodes.toSet()
        return dataset.subRectangles.filter { it.seaOverlapping && it.subCode in codes }
    }
}
