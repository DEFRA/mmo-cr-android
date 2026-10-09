package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata

import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local.ReferenceDataGearEntity
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local.ReferenceDataGearMeasurementEntity
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local.ReferenceDataPortEntity
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local.ReferenceDataSpeciesEntity
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local.ReferenceDataVesselEntity
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementField
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Port
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Species
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Vessel

/** Entity → domain mapping for the cached reference-data tables (ADR 0015); the cache-hit read path. */
internal fun ReferenceDataVesselEntity.toDomain(): Vessel =
    Vessel(id = id, name = displayName, lengthOverallMetres = lengthOverallMetres)

internal fun ReferenceDataPortEntity.toDomain(): Port =
    Port(id = id, name = displayName, statisticalAreaId = statisticalAreaId)

/** Quota/precision stay the existing domain defaults — cache storage mirrors the API's own scope (ADR 0015). */
internal fun ReferenceDataSpeciesEntity.toDomain(): Species = Species(id = id, name = displayName, faoCode = faoCode)

internal fun ReferenceDataGearMeasurementEntity.toDomain(): GearMeasurementField =
    GearMeasurementField(key = key, label = label, type = GearMeasurementFieldType.valueOf(type), unit = unit)

/** Groups a band's flat measurement rows by gear before building each [GearType]; both lists are pre-sorted. */
internal fun List<ReferenceDataGearEntity>.toDomainGearTypes(
    measurementsByGearId: Map<String, List<ReferenceDataGearMeasurementEntity>>,
): List<GearType> =
    map { gear ->
        GearType(
            id = gear.id,
            name = gear.name,
            measurementFields = measurementsByGearId[gear.id].orEmpty().map { it.toDomain() },
        )
    }
