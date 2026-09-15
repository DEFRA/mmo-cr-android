package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata

import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementField
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldKeys
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Port
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.ReferenceDataRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Species
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.StatisticalSubRectangle
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Vessel

/**
 * Bundled, local, read-only reference-data stub — see ADR 0008. Includes the confirmed Stage-1 screenshot
 * data points (vessels ACHILLES/HERCULES; ports Hastings/Dover) plus placeholder gear/species/stat-area
 * rows (clearly marked TBC) to unblock later-phase unit tests before real data/screenshots are available.
 *
 * All confirmed gear-type "Mesh size (mm)" fields — Seine nets, Bottom otter trawls, and Gillnets
 * (circling) — are [GearMeasurementFieldType.Integer], consistent with the universal "All gear
 * measurements must be whole numbers." instruction shown on every gear measurement screen. Seine nets and
 * Bottom otter trawls originally used [GearMeasurementFieldType.Decimal] (predating the later screenshots
 * that confirmed the whole-number-only rule); this was retrofitted to Integer once a real Gillnets
 * screenshot confirmed mesh size is whole-number for every gear type, removing the previously-flagged
 * inconsistency.
 */
class StubReferenceDataRepository : ReferenceDataRepository {
    private val statAreaHastings = "AREA-HASTINGS"
    private val statAreaDover = "AREA-DOVER"

    private val vessels =
        listOf(
            Vessel(id = "vessel-achilles", name = "ACHILLES"),
            Vessel(id = "vessel-hercules", name = "HERCULES"),
        )

    private val ports =
        listOf(
            Port(id = "port-hastings", name = "Hastings", statisticalAreaId = statAreaHastings),
            Port(id = "port-dover", name = "Dover", statisticalAreaId = statAreaDover),
        )

    private val previouslyUsedPortsByVessel =
        mapOf(
            "vessel-achilles" to listOf("port-hastings", "port-dover"),
            "vessel-hercules" to emptyList(),
        )

    // Shared by the "Pots" and "Traps" gear types below — confirmed via screenshot to be identical schemas
    // for two otherwise-distinct, separately selectable gear types.
    private val potsOrTrapsMeasurementFields =
        listOf(
            GearMeasurementField(
                key = GearMeasurementFieldKeys.TOTAL_POTS_OR_TRAPS_HAULED,
                label = "Total pots or traps hauled",
                type = GearMeasurementFieldType.Integer,
            ),
            GearMeasurementField(
                key = GearMeasurementFieldKeys.TOTAL_POTS_OR_TRAPS_LEFT_IN_WATER,
                label = "Total pots or traps left in water",
                type = GearMeasurementFieldType.Integer,
            ),
        )

    // Confirmed Phase-3 screenshot gear types (search suggestion names/codes) plus their measurement
    // field schemas where confirmed; Beam trawls/Bottom pair trawls are confirmed to exist as selectable
    // gear types but their measurement fields are not yet screenshotted, so their schema is left empty
    // (TODO Phase 3 follow-up) rather than invented.
    private val gearTypes =
        listOf(
            GearType(
                id = "gear-seine-nets",
                name = "Seine nets (not specified)",
                measurementFields =
                    listOf(
                        GearMeasurementField(
                            key = GearMeasurementFieldKeys.MESH_SIZE_MM,
                            label = "Mesh size (mm)",
                            type = GearMeasurementFieldType.Integer,
                            unit = "mm",
                        ),
                    ),
            ),
            // TODO(Phase 3 follow-up): measurement fields TBC, pending screenshots beyond mesh-size/
            // trawl-net-count (only Seine nets and Bottom otter trawls are confirmed so far).
            GearType(id = "gear-beam-trawls-tbb", name = "Beam trawls (TBB)"),
            GearType(id = "gear-bottom-pair-trawls-ptb", name = "Bottom pair trawls (PTB)"),
            GearType(
                id = "gear-bottom-otter-trawls-tb",
                name = "Bottom otter trawls (TB)",
                measurementFields =
                    listOf(
                        GearMeasurementField(
                            key = GearMeasurementFieldKeys.NUMBER_OF_TRAWL_NETS,
                            label = "Number of trawl nets",
                            type = GearMeasurementFieldType.Integer,
                        ),
                        GearMeasurementField(
                            key = GearMeasurementFieldKeys.MESH_SIZE_MM,
                            label = "Mesh size (mm)",
                            type = GearMeasurementFieldType.Integer,
                            unit = "mm",
                        ),
                    ),
            ),
            // TBC: placeholder-only gear types — fields not legible/confirmed in the available screenshots,
            // so left as name-only entries rather than inventing a schema.
            GearType(id = "gear-diving", name = "Diving (TBC)"),
            GearType(id = "gear-dredge", name = "Dredge (TBC)"),
            // Pots and Traps are confirmed as two distinct, separately selectable gear-type entries that
            // happen to share the exact same two-field schema (per the confirmed screenshots) — modelled
            // as two GearType rows, not merged into one, so each keeps its own id/GearUse identity.
            GearType(
                id = "gear-pots",
                name = "Pots",
                measurementFields = potsOrTrapsMeasurementFields,
            ),
            GearType(
                id = "gear-traps",
                name = "Traps",
                measurementFields = potsOrTrapsMeasurementFields,
            ),
            GearType(
                id = "gear-handlines",
                name = "Handlines and pole lines (hand operated)",
                measurementFields =
                    listOf(
                        GearMeasurementField(
                            key = GearMeasurementFieldKeys.NUMBER_OF_RODS_AND_LINES,
                            label = "Number of rods and lines",
                            type = GearMeasurementFieldType.Integer,
                        ),
                    ),
                // Confirmed screenshot title is the shorter "handlines", not the full search/display name.
                measurementTitleOverride = "handlines",
            ),
            // "Drifting longlines" is the one confirmed Longlines-category gear type so far; other
            // longline variants (e.g. set longlines) are not yet confirmed and are not invented here.
            GearType(
                id = "gear-drifting-longlines",
                name = "Drifting longlines",
                measurementFields =
                    listOf(
                        GearMeasurementField(
                            key = GearMeasurementFieldKeys.TOTAL_HOOKS_HAULED,
                            label = "Total hooks hauled",
                            type = GearMeasurementFieldType.Integer,
                        ),
                        GearMeasurementField(
                            key = GearMeasurementFieldKeys.TOTAL_HOOKS_LEFT_IN_WATER,
                            label = "Total hooks left in water",
                            type = GearMeasurementFieldType.Integer,
                        ),
                    ),
            ),
            // "Gillnets (circling)" is one confirmed gear type within the broader Nets/Gillnets and
            // Trammels category — the general category placeholder below is kept distinct/TBC since other
            // variants in that category (e.g. gillnets (fixed), trammel nets) are not yet confirmed.
            GearType(
                id = "gear-gillnets-circling",
                name = "Gillnets (circling)",
                measurementFields =
                    listOf(
                        // Whole-number, consistent with every other confirmed gear type's mesh-size field
                        // (Seine nets and Bottom otter trawls were retrofitted from Decimal to Integer once
                        // this Gillnets screenshot confirmed the whole-number rule applies universally).
                        GearMeasurementField(
                            key = GearMeasurementFieldKeys.MESH_SIZE_MM,
                            label = "Mesh size (mm)",
                            type = GearMeasurementFieldType.Integer,
                            unit = "mm",
                        ),
                        GearMeasurementField(
                            key = GearMeasurementFieldKeys.TOTAL_LENGTH_OF_NETS_HAULED_M,
                            label = "Total length of nets hauled (m)",
                            type = GearMeasurementFieldType.Integer,
                            unit = "m",
                        ),
                        GearMeasurementField(
                            key = GearMeasurementFieldKeys.TOTAL_LENGTH_OF_NETS_LEFT_IN_WATER_M,
                            label = "Total length of nets left in water (m)",
                            type = GearMeasurementFieldType.Integer,
                            unit = "m",
                        ),
                    ),
            ),
            GearType(id = "gear-nets-gillnets-trammels", name = "Nets/Gillnets and trammels (TBC)"),
            GearType(id = "gear-trawls-not-specified", name = "Trawls (not specified) (TBC)"),
        )

    // TBC: placeholder species only, pending confirmed reference data.
    private val species =
        listOf(
            Species(id = "species-cod", name = "Cod (TBC)", faoCode = "COD"),
            Species(id = "species-plaice", name = "Plaice (TBC)", faoCode = "PLE"),
            Species(id = "species-crab", name = "Brown crab (TBC)", faoCode = "CRE"),
        )

    // TBC: placeholder statistical sub-rectangles only, pending confirmed reference data.
    private val statisticalSubRectangles =
        listOf(
            StatisticalSubRectangle(id = "rect-hastings-1", code = "29E7 (TBC)", statisticalAreaId = statAreaHastings),
            StatisticalSubRectangle(id = "rect-dover-1", code = "31F1 (TBC)", statisticalAreaId = statAreaDover),
        )

    override suspend fun getVessels(): Result<List<Vessel>> = Result.success(vessels)

    override suspend fun getPorts(): Result<List<Port>> = Result.success(ports)

    override suspend fun getPreviouslyUsedPorts(vesselId: String): Result<List<Port>> =
        Result.success(previouslyUsedPortsByVessel[vesselId].orEmpty().mapNotNull(::findPortById))

    override suspend fun getGearTypes(): Result<List<GearType>> = Result.success(gearTypes)

    override suspend fun getSpecies(): Result<List<Species>> = Result.success(species)

    override suspend fun getStatisticalSubRectangles(): Result<List<StatisticalSubRectangle>> =
        Result.success(statisticalSubRectangles)

    override suspend fun getStatisticalSubRectanglesForPort(portId: String): Result<List<StatisticalSubRectangle>> {
        val port = findPortById(portId)
        return if (port == null) {
            Result.failure(NoSuchElementException("Unknown port id: $portId"))
        } else {
            Result.success(statisticalSubRectangles.filter { it.statisticalAreaId == port.statisticalAreaId })
        }
    }

    private fun findPortById(portId: String): Port? = ports.firstOrNull { it.id == portId }
}
