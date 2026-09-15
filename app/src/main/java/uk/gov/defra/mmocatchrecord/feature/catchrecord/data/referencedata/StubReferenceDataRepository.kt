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
                            type = GearMeasurementFieldType.Decimal,
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
                            type = GearMeasurementFieldType.Decimal,
                            unit = "mm",
                        ),
                    ),
            ),
            // TBC: placeholder-only gear types from the confirmed gear-type category list, names not yet
            // screenshotted in full and fields not yet confirmed — unblocks later-phase development
            // without inventing unconfirmed reference data.
            GearType(id = "gear-diving", name = "Diving (TBC)"),
            GearType(id = "gear-dredge", name = "Dredge (TBC)"),
            GearType(id = "gear-pots-and-traps", name = "Pots and traps (TBC)"),
            GearType(id = "gear-handlines", name = "Handlines (TBC)"),
            GearType(id = "gear-longlines", name = "Longlines (TBC)"),
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
