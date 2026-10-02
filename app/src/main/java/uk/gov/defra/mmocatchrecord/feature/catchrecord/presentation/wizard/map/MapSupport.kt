package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.map

import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.MeasurementValue
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldKeys
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Port
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.StatisticalSubRectangle
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.nextGearUsePendingStatRectangle
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear.GearMeasurementSupport

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

    /**
     * Resolves the gear use `MapScreen` is collecting a statistical sub-rectangle for: the gear use
     * matching [editGearUseId] on the check-your-answers edit path, or the next confirmed gear use still
     * missing a code (see [nextGearUsePendingStatRectangle]) on the normal add path.
     */
    fun currentGearUseFor(
        draft: CatchRecordDraft?,
        editGearUseId: String?,
    ): GearUse? =
        if (editGearUseId != null) {
            draft?.gearUses?.firstOrNull { it.id == editGearUseId }
        } else {
            draft?.let(::nextGearUsePendingStatRectangle)
        }

    /** Returns [draft] with [gearUse]'s statistical sub-rectangle set to [code] — see `MapScreen`. */
    fun withStatRectangleCode(
        draft: CatchRecordDraft,
        gearUse: GearUse,
        code: String,
    ): CatchRecordDraft {
        val updatedGearUse = gearUse.copy(statisticalSubRectangleCode = code)
        return draft.copy(
            gearUses = draft.gearUses.map { if (it.id == updatedGearUse.id) updatedGearUse else it },
        )
    }
}
