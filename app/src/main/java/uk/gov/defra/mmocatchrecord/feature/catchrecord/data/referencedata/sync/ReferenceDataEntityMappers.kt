package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.sync

import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local.ReferenceDataGearEntity
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local.ReferenceDataGearMeasurementEntity
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local.ReferenceDataPortEntity
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local.ReferenceDataSpeciesEntity
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local.ReferenceDataVesselEntity
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.remote.GearMeasurementDto
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.remote.GearMobileDto
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.remote.PortMobileDto
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.remote.PortStatisticalAreaLookup
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.remote.SpeciesMobileDto
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.remote.VesselMobileDto
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.remote.gearMeasurementKeyFor
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.remote.gearMeasurementTypeFor
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.remote.gearMeasurementUnitFor

internal fun VesselMobileDto.toEntity(): ReferenceDataVesselEntity =
    ReferenceDataVesselEntity(
        id = id,
        name = name,
        displayName = displayName,
        pln = pln,
        cfr = cfr,
        lengthOverallMetres = lengthOverallMetres,
    )

internal fun PortMobileDto.toEntity(): ReferenceDataPortEntity =
    ReferenceDataPortEntity(
        id = id,
        code = code,
        name = name,
        displayName = displayName,
        latitude = coordinate?.latitude,
        longitude = coordinate?.longitude,
        statisticalAreaId = PortStatisticalAreaLookup.statisticalAreaId(code),
    )

internal fun SpeciesMobileDto.toEntity(): ReferenceDataSpeciesEntity =
    ReferenceDataSpeciesEntity(
        id = id,
        faoCode = faoCode,
        scientificName = scientificName,
        displayName = displayName,
    )

internal fun GearMobileDto.toEntity(
    band: String,
    orderIndex: Int,
): ReferenceDataGearEntity =
    ReferenceDataGearEntity(
        id = id,
        vesselLengthBand = band,
        code = code,
        name = name,
        categoryId = category.id,
        categoryCode = category.code,
        categoryName = category.name,
        pairFishing = pairFishing,
        orderIndex = orderIndex,
    )

/** Required-then-variable, in order, skipping any id absent from [measurementsById] (mirrors the domain mapper). */
internal fun GearMobileDto.measurementEntitiesFor(
    band: String,
    measurementsById: Map<String, GearMeasurementDto>,
): List<ReferenceDataGearMeasurementEntity> =
    (requiredMeasurementIds + variableMeasurementIds)
        .mapNotNull { measurementsById[it] }
        .mapIndexed { index, measurement ->
            ReferenceDataGearMeasurementEntity(
                gearId = id,
                vesselLengthBand = band,
                key = gearMeasurementKeyFor(measurement.code),
                label = measurement.label,
                type = gearMeasurementTypeFor(measurement.unit).name,
                unit = gearMeasurementUnitFor(measurement.unit),
                orderIndex = index,
            )
        }
