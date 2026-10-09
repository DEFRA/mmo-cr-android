package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.remote

import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementField
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldKeys
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Port
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Species
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Vessel

private const val UNIT_COUNT = "count"
private const val UNIT_METRES = "m"
private const val UNIT_MILLIMETRES = "mm"

internal fun VesselMobileDto.toDomain(): Vessel = Vessel(id = id, name = displayName)

/**
 * The service does not yet expose a port→statistical-area mapping (ADR 0015 interim default, Q3) — every
 * port code sentinel-falls-back to [UNASSIGNED] until a future phase/backend change supplies real data.
 */
internal object PortStatisticalAreaLookup {
    const val UNASSIGNED = "AREA-UNASSIGNED"
    private val byPortCode: Map<String, String> = emptyMap()

    fun statisticalAreaId(portCode: String): String = byPortCode[portCode] ?: UNASSIGNED
}

internal fun PortMobileDto.toDomain(): Port =
    Port(
        id = id,
        name = displayName,
        statisticalAreaId = PortStatisticalAreaLookup.statisticalAreaId(code),
    )

/** Quota/precision are not yet part of the API contract (ADR 0015 interim default, Q6) — domain defaults. */
internal fun SpeciesMobileDto.toDomain(): Species = Species(id = id, name = displayName, faoCode = faoCode)

/**
 * Translates a live API gear-measurement `code` to the persisted [GearMeasurementField.key] space — full
 * table and rationale in ADR 0015. `ME` reuses [GearMeasurementFieldKeys.MESH_SIZE_MM]; others are new keys.
 */
internal fun gearMeasurementKeyFor(code: String): String =
    when (code) {
        "ME" -> GearMeasurementFieldKeys.MESH_SIZE_MM
        else -> "api_gear_measurement_${code.lowercase()}"
    }

/** `count` → [GearMeasurementFieldType.Integer]; `m`/`mm` → [GearMeasurementFieldType.Decimal]; any other
 *  or missing unit also defaults to [GearMeasurementFieldType.Decimal] (ADR 0015).
 */
internal fun gearMeasurementTypeFor(unit: String?): GearMeasurementFieldType =
    if (unit == UNIT_COUNT) GearMeasurementFieldType.Integer else GearMeasurementFieldType.Decimal

/** Only a real physical unit is carried into the domain field; `count` is not a displayable unit. */
internal fun gearMeasurementUnitFor(unit: String?): String? =
    if (unit == UNIT_METRES || unit == UNIT_MILLIMETRES) unit else null

internal fun GearMeasurementDto.toDomain(): GearMeasurementField =
    GearMeasurementField(
        key = gearMeasurementKeyFor(code),
        label = label,
        type = gearMeasurementTypeFor(unit),
        unit = gearMeasurementUnitFor(unit),
    )

/**
 * Resolves this gear's fields from the *same band response*'s measurement catalogue, required-then-
 * variable; an id absent from [measurementsById] (a different band) is skipped — how banding applies.
 */
internal fun GearMobileDto.toDomain(measurementsById: Map<String, GearMeasurementDto>): GearType {
    val orderedIds = requiredMeasurementIds + variableMeasurementIds
    val fields = orderedIds.mapNotNull { measurementsById[it]?.toDomain() }
    return GearType(id = id, name = name, measurementFields = fields)
}
