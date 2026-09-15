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

/** A gear type selectable for a [uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse]. */
data class GearType(
    val id: String,
    val name: String,
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
