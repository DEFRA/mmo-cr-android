@file:Suppress("detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata

/** A registered fishing vessel selectable in the wizard. */
data class Vessel(
    val id: String,
    val name: String,
)

/** A UK/EU landing port. */
data class Port(
    val id: String,
    val name: String,
    val statisticalAreaId: String,
)

/** The kind of numeric input a gear-type measurement field requires (see [GearMeasurementField]). */
enum class GearMeasurementFieldType {
    Integer,
    Decimal,
}

/**
 * One measurement field in a [GearType]'s schema, e.g. "Mesh size (mm)". [key] is the stable identifier
 * persisted against [uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse.measurements];
 * [label] is the display text shown on the gear-measurement screen (deliberately plain, unlocalized
 * content, consistent with [GearType.name]/[Species.name]/[Port.name] elsewhere in this file); [unit] (if
 * any) is stored alongside the captured value — see
 * [uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.MeasurementValue.Numeric.unit].
 */
data class GearMeasurementField(
    val key: String,
    val label: String,
    val type: GearMeasurementFieldType,
    val unit: String? = null,
)

/**
 * Stable [GearMeasurementField.key] constants, shared between the reference-data schema (below) and any
 * presentation-layer code that needs to look a specific field's captured value up by key — e.g. deriving
 * the gear-summary-checklist's "100mm mesh" secondary text from a gear use's captured measurements.
 */
object GearMeasurementFieldKeys {
    const val MESH_SIZE_MM = "mesh_size_mm"
    const val NUMBER_OF_TRAWL_NETS = "number_of_trawl_nets"
    const val TOTAL_POTS_OR_TRAPS_HAULED = "total_pots_or_traps_hauled"
    const val TOTAL_POTS_OR_TRAPS_LEFT_IN_WATER = "total_pots_or_traps_left_in_water"
    const val NUMBER_OF_RODS_AND_LINES = "number_of_rods_and_lines"
    const val TOTAL_HOOKS_HAULED = "total_hooks_hauled"
    const val TOTAL_HOOKS_LEFT_IN_WATER = "total_hooks_left_in_water"
    const val TOTAL_LENGTH_OF_NETS_HAULED_M = "total_length_of_nets_hauled_m"
    const val TOTAL_LENGTH_OF_NETS_LEFT_IN_WATER_M = "total_length_of_nets_left_in_water_m"
}

/** A gear type selectable for a [uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse]. */
data class GearType(
    val id: String,
    val name: String,
    /**
     * The gear-specific measurement fields shown on the gear-measurement screen for this gear type, in
     * display order. Empty for gear types whose fields are not yet confirmed (blocked on later-phase
     * screenshots) — see
     * [uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.StubReferenceDataRepository].
     */
    val measurementFields: List<GearMeasurementField> = emptyList(),
    /**
     * Overrides the gear name shown in "Enter the measurements for {gear}" when it must differ from the
     * lowercased [name]/reference-data display name — confirmed so far only for "Handlines and pole lines
     * (hand operated)", whose measurement-screen title is the shorter "handlines". `null` (the default)
     * means [uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.GearMeasurementSupport]
     * falls back to its usual lowercased-display-name derivation.
     */
    val measurementTitleOverride: String? = null,
)

/** A species selectable for a species/weight entry. */
data class Species(
    val id: String,
    val name: String,
    val faoCode: String,
)

/** An ICES statistical sub-rectangle selectable for a [uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse]. */
data class StatisticalSubRectangle(
    val id: String,
    val code: String,
    val statisticalAreaId: String,
)

/**
 * Reference (lookup) data required by the catch-record wizard: vessels, ports, gear types, species,
 * statistical sub-rectangles, and the portâ†’statistical-area mapping (via [Port.statisticalAreaId]).
 *
 * Stage-1/Phase-0 has only a bundled local stub implementation â€” see ADR 0008. This interface is shaped so
 * a future network/cache-backed implementation is a drop-in replacement with no caller changes.
 */
interface ReferenceDataRepository {
    suspend fun getVessels(): Result<List<Vessel>>

    suspend fun getPorts(): Result<List<Port>>

    /** Returns previously used ports for [vesselId], most-recent-first for shortcut/favourites use. */
    suspend fun getPreviouslyUsedPorts(vesselId: String): Result<List<Port>>

    suspend fun getGearTypes(): Result<List<GearType>>

    suspend fun getSpecies(): Result<List<Species>>

    suspend fun getStatisticalSubRectangles(): Result<List<StatisticalSubRectangle>>

    /** Statistical sub-rectangles whose [StatisticalSubRectangle.statisticalAreaId] matches [port]'s area. */
    suspend fun getStatisticalSubRectanglesForPort(portId: String): Result<List<StatisticalSubRectangle>>
}
