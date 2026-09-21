@file:Suppress("detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow

import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementField
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldKeys
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Port
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.ReferenceDataRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Species
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.SpeciesWeightPrecision
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.StatisticalSubRectangle
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Vessel

class FakeReferenceDataRepository(
    private val vessels: List<Vessel> =
        listOf(Vessel("vessel-achilles", "ACHILLES"), Vessel("vessel-hercules", "HERCULES")),
    private val ports: List<Port> =
        listOf(Port("port-hastings", "Hastings", "AREA-HASTINGS"), Port("port-dover", "Dover", "AREA-DOVER")),
    private val previousPortsByVessel: Map<String, List<Port>> =
        mapOf(
            "vessel-achilles" to
                listOf(Port("port-hastings", "Hastings", "AREA-HASTINGS"), Port("port-dover", "Dover", "AREA-DOVER")),
            "vessel-hercules" to emptyList(),
        ),
    private val statisticalSubRectangles: List<StatisticalSubRectangle> =
        listOf(
            StatisticalSubRectangle("rect-38e95", "38E95", "AREA-HASTINGS"),
            StatisticalSubRectangle("rect-38e98", "38E98", "AREA-HASTINGS"),
            StatisticalSubRectangle("rect-38f02", "38F02", "AREA-HASTINGS"),
            StatisticalSubRectangle("rect-30f10", "30F10", "AREA-DOVER"),
        ),
    private val gearTypes: List<GearType> =
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
            GearType(
                id = "gear-pots",
                name = "Pots",
                measurementFields = potsOrTrapsFields,
            ),
            GearType(
                id = "gear-traps",
                name = "Traps",
                measurementFields = potsOrTrapsFields,
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
                measurementTitleOverride = "handlines",
            ),
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
            GearType(
                id = "gear-gillnets-circling",
                name = "Gillnets (circling)",
                measurementFields =
                    listOf(
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
        ),
    private val species: List<Species> =
        listOf(
            Species(
                id = "species-cod",
                name = "Atlantic cod (COD)",
                faoCode = "COD",
                weightPrecision = SpeciesWeightPrecision.OneDecimalPlace,
                weightAboveMinimumSizeMandatory = true,
                monthlyQuotaKg = 50.0,
                annualQuotaKg = 500.0,
            ),
            Species(
                id = "species-plaice",
                name = "Plaice (TBC)",
                faoCode = "PLE",
                weightPrecision = SpeciesWeightPrecision.WholeNumber,
                weightAboveMinimumSizeMandatory = false,
                monthlyQuotaKg = null,
                annualQuotaKg = null,
            ),
        ),
) : ReferenceDataRepository {
    companion object {
        // Shared by the "Pots" and "Traps" default gear types above — mirrors StubReferenceDataRepository.
        private val potsOrTrapsFields =
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
    }

    override suspend fun getVessels(): Result<List<Vessel>> = Result.success(vessels)

    override suspend fun getPorts(): Result<List<Port>> = Result.success(ports)

    override suspend fun getPreviouslyUsedPorts(vesselId: String): Result<List<Port>> =
        Result.success(previousPortsByVessel[vesselId].orEmpty())

    override suspend fun getGearTypes(): Result<List<GearType>> = Result.success(gearTypes)

    override suspend fun getSpecies(): Result<List<Species>> = Result.success(species)

    override suspend fun getStatisticalSubRectangles(): Result<List<StatisticalSubRectangle>> =
        Result.success(statisticalSubRectangles)

    override suspend fun getStatisticalSubRectanglesForPort(portId: String): Result<List<StatisticalSubRectangle>> {
        val port = ports.firstOrNull { it.id == portId } ?: return Result.success(emptyList())
        return Result.success(statisticalSubRectangles.filter { it.statisticalAreaId == port.statisticalAreaId })
    }
}
