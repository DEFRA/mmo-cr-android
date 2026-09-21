package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear

import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.MeasurementValue
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldKeys
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearType

/**
 * Pure (no Android/Compose dependency, directly unit-testable) helpers shared by the gear-measurement and
 * gear-summary screens.
 */
object GearMeasurementSupport {
    /**
     * Strips any parenthetical reference-code/qualifier suffix from a gear type's reference-data name
     * (e.g. "Bottom otter trawls (TB)" -> "Bottom otter trawls", "Seine nets (not specified)" -> "Seine
     * nets"), for use anywhere the plain gear name (without its code/qualifier) is wanted.
     */
    fun displayNameFor(gearType: GearType): String = gearType.name.substringBefore("(").trim()

    /**
     * The gear-name portion of "Enter the measurements for {gear}", lowercased per the confirmed
     * screenshots (e.g. "seine nets", "bottom otter trawls", "pots", "traps", "drifting longlines",
     * "gillnets").
     *
     * Known, accepted deviation: the confirmed screenshots use a singular form for some gear types (e.g.
     * "bottom otter trawl") where the underlying reference-data name is plural ("Bottom otter trawls
     * (TB)"), and no separate singular/short-name field has been confirmed — so this always yields the
     * plural reference-data name as typed. Minor, non-blocking; revisit if a singular display name is
     * confirmed in a later phase.
     *
     * "Handlines and pole lines (hand operated)" is the one gear type confirmed so far whose measurement
     * screen title is *shorter* than its stripped display name ("handlines", not "handlines and pole
     * lines") — that case is handled via [GearType.measurementTitleOverride] rather than a derivation rule.
     */
    fun titleGearNameFor(gearType: GearType): String =
        gearType.measurementTitleOverride ?: displayNameFor(gearType).lowercase()

    /**
     * The gear-summary checklist's secondary/greyed measurement-summary line for one gear use (e.g.
     * "100mm mesh"), or `null` if it has no captured mesh-size measurement. Only mesh-size has a confirmed
     * summary format so far (per the Phase 3 screenshots); other measurement fields (e.g. number of trawl
     * nets) are not shown in the summary line until a confirmed format is provided.
     */
    fun measurementSummaryFor(gearUse: GearUse): String? {
        val meshSize =
            (gearUse.measurements[GearMeasurementFieldKeys.MESH_SIZE_MM] as? MeasurementValue.Numeric)?.value
                ?: return null
        return "${formatNumber(meshSize)}mm mesh"
    }

    /** Renders a whole-number [Double] without a trailing ".0" (e.g. `100.0` -> `"100"`, `12.5` -> `"12.5"`). */
    fun formatNumber(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
}
