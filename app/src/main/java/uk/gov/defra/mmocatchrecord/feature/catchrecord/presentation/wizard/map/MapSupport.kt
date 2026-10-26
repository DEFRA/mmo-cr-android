package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.map

import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.MeasurementValue
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldKeys
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Port
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.StatisticalSubRectangle
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.nextGearUsePendingStatRectangle
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.resolveWizardGearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear.GearMeasurementSupport
import uk.gov.defra.mmocatchrecord.mapdata.MapDataset
import uk.gov.defra.mmocatchrecord.mapdata.Projection
import uk.gov.defra.mmocatchrecord.mapdata.SerializableSubRectangle

/**
 * Pure (no Android/Compose dependency) helpers for the Phase 4 per-gear statistical sub-rectangle step —
 * see `MapScreen`.
 */
object MapSupport {
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
     * [Port.statisticalAreaId]), or empty if the port or its area is unknown. Feeds the grid (screen 1) and
     * radio-list (screen 2); the "Other" autocomplete search (screen 3) instead searches [allRectangles]
     * unfiltered.
     */
    fun nearbyRectanglesFor(
        departurePort: Port?,
        allRectangles: List<StatisticalSubRectangle>,
    ): List<StatisticalSubRectangle> =
        departurePort
            ?.let { port -> allRectangles.filter { it.statisticalAreaId == port.statisticalAreaId } }
            .orEmpty()

    /** Max nearby codes returned by [nearbyRectanglesFromDataset] — mirrors the stub's previous
     *  typical per-port count. */
    private const val NEARBY_RECTANGLE_LIMIT = 9

    /**
     * Real-geography replacement for [nearbyRectanglesFor], used once the offline [MapDataset] has loaded:
     * the [NEARBY_RECTANGLE_LIMIT] sea-overlapping (selectable) sub-rectangles whose centroid is nearest
     * [departurePort]'s real coordinates (matched by name against `dataset.ports`), ranked by straight-line
     * Web Mercator distance. [nearbyRectanglesFor]'s `statisticalAreaId` grouping only ever covered the
     * reference-data stub's two placeholder ports and, per docs/development/offline-map.md's "Known
     * data-integration gap", included codes with no real relation to that port's geography — this instead
     * works for any port present in the bundled `ports.geojson`. Returns empty if the port can't be matched
     * by name (never crash — see `MapScreenStatusContent`).
     */
    fun nearbyRectanglesFromDataset(
        departurePort: Port?,
        dataset: MapDataset,
    ): List<StatisticalSubRectangle> {
        val matchedPort =
            departurePort?.let { port ->
                dataset.ports.firstOrNull { normalisedName(it.name) == normalisedName(port.name) }
            } ?: return emptyList()
        val portMetres = Projection.wgs84ToMetres(matchedPort.point.lon, matchedPort.point.lat)
        return dataset.subRectangles
            .asSequence()
            .filter { it.isSeaOverlapping }
            .sortedBy { rectangle ->
                val rectangleMetres = Projection.wgs84ToMetres(rectangle.centroid.lon, rectangle.centroid.lat)
                val dx = rectangleMetres[0] - portMetres[0]
                val dy = rectangleMetres[1] - portMetres[1]
                dx * dx + dy * dy
            }.take(NEARBY_RECTANGLE_LIMIT)
            .map(::toStatisticalSubRectangle)
            .toList()
    }

    /**
     * The full/global sub-rectangle code list backing the "Other" free-text search (screen 3) and FR8
     * existence validation once the offline [MapDataset] has loaded — replaces the incomplete
     * reference-data stub list (see [nearbyRectanglesFromDataset]) with every real, sea-overlapping
     * statistical sub-rectangle code.
     */
    fun allRectanglesFromDataset(dataset: MapDataset): List<StatisticalSubRectangle> =
        dataset.subRectangles.filter { it.isSeaOverlapping }.map(::toStatisticalSubRectangle)

    // Dataset-sourced rectangles have no reference-data `statisticalAreaId` grouping (nearby selection is
    // by real geographic distance instead — see [nearbyRectanglesFromDataset]), so it's left blank.
    private fun toStatisticalSubRectangle(rectangle: SerializableSubRectangle): StatisticalSubRectangle =
        StatisticalSubRectangle(
            id = rectangle.code,
            code = rectangle.code,
            statisticalAreaId = "",
            centroidLatitude = rectangle.centroid.lat,
            centroidLongitude = rectangle.centroid.lon,
            icesRectangleName = rectangle.icesName.takeIf { it.isNotBlank() },
        )

    private fun normalisedName(value: String) = value.trim().lowercase()

    /**
     * Resolves [code] against [options] (the "Other" search's full dataset-or-stub list, preferring the
     * nearby list as a fallback) so its coordinates/ICES name can be attached at save time — see
     * `MapScreenStatusContent`'s `onSubmit`.
     */
    fun rectangleForCode(
        code: String,
        options: StatRectangleOptions,
    ): StatisticalSubRectangle? = (options.all + options.nearby).firstOrNull { it.code.equals(code, ignoreCase = true) }

    /**
     * Resolves the gear use `MapScreen` is collecting a statistical sub-rectangle for: the gear use
     * matching [editGearUseId] on the check-your-answers edit path, or the next confirmed gear use still
     * missing a code (see [nextGearUsePendingStatRectangle]) on the normal add path.
     */
    fun currentGearUseFor(
        draft: CatchRecordDraft?,
        editGearUseId: String?,
        boundGearUseId: String? = null,
    ): GearUse? = resolveWizardGearUse(draft, editGearUseId, boundGearUseId, ::nextGearUsePendingStatRectangle)

    /** Returns [draft] with [gearUse]'s statistical sub-rectangle (and its centre, if known) set — see `MapScreen`. */
    fun withStatRectangleCode(
        draft: CatchRecordDraft,
        gearUse: GearUse,
        code: String,
        latitude: Double? = null,
        longitude: Double? = null,
    ): CatchRecordDraft {
        val updatedGearUse =
            gearUse.copy(
                statisticalSubRectangleCode = code,
                statisticalSubRectangleLatitude = latitude,
                statisticalSubRectangleLongitude = longitude,
            )
        return draft.copy(
            gearUses = draft.gearUses.map { if (it.id == updatedGearUse.id) updatedGearUse else it },
        )
    }
}
