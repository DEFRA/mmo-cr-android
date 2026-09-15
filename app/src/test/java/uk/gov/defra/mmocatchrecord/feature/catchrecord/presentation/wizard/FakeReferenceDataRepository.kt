@file:Suppress("detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard

import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Port
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.ReferenceDataRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Species
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
) : ReferenceDataRepository {
    override suspend fun getVessels(): Result<List<Vessel>> = Result.success(vessels)

    override suspend fun getPorts(): Result<List<Port>> = Result.success(ports)

    override suspend fun getPreviouslyUsedPorts(vesselId: String): Result<List<Port>> =
        Result.success(previousPortsByVessel[vesselId].orEmpty())

    override suspend fun getGearTypes(): Result<List<GearType>> = Result.success(emptyList())

    override suspend fun getSpecies(): Result<List<Species>> = Result.success(emptyList())

    override suspend fun getStatisticalSubRectangles(): Result<List<StatisticalSubRectangle>> =
        Result.success(emptyList())

    override suspend fun getStatisticalSubRectanglesForPort(portId: String): Result<List<StatisticalSubRectangle>> =
        Result.success(emptyList())
}
