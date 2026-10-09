package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.remote

import kotlinx.serialization.Serializable

/**
 * Common envelope fields every reference-data response shares (ADR 0015), implemented by both
 * [ReferenceDataEnvelopeDto] and [GearsEnvelopeDto] so one response handler covers both.
 */
internal interface ReferenceDataEnvelopeMetadata {
    val dataset: String
    val collectionId: String
    val schemaVersion: String
    val version: String
}

@Serializable
internal data class ReferenceDataEnvelopeDto<T>(
    override val dataset: String,
    override val collectionId: String,
    override val schemaVersion: String,
    override val version: String,
    val view: String,
    val total: Int,
    val items: List<T>,
    val offset: Int? = null,
    val limit: Int? = null,
) : ReferenceDataEnvelopeMetadata

@Serializable
internal data class VesselMobileDto(
    val id: String,
    val name: String,
    val displayName: String,
    val pln: String? = null,
    val cfr: String? = null,
    val lengthOverallMetres: Double? = null,
)

@Serializable
internal data class PortCoordinateDto(
    val latitude: Double,
    val longitude: Double,
)

@Serializable
internal data class PortMobileDto(
    val id: String,
    val code: String,
    val name: String,
    val displayName: String,
    val coordinate: PortCoordinateDto? = null,
)

@Serializable
internal data class SpeciesMobileDto(
    val id: String,
    val faoCode: String,
    val displayName: String,
    val scientificName: String? = null,
)

@Serializable
internal data class GearCategoryDto(
    val id: String,
    val code: String,
    val name: String,
)

@Serializable
internal data class GearMobileDto(
    val id: String,
    val code: String,
    val name: String,
    val category: GearCategoryDto,
    val pairFishing: Boolean = false,
    val requiredMeasurementIds: List<String> = emptyList(),
    val variableMeasurementIds: List<String> = emptyList(),
)

@Serializable
internal data class GearMeasurementDto(
    val id: String,
    val code: String,
    val label: String,
    val kind: String,
    val unit: String? = null,
    val minimumValue: Double? = null,
    val maximumValue: Double? = null,
)

/** Only present (per-request) when the `vesselLengthMetres` query parameter was supplied (ADR 0015). */
@Serializable
internal data class GearsContextDto(
    val vesselLengthBand: String? = null,
)

@Serializable
internal data class GearsEnvelopeDto(
    override val dataset: String,
    override val collectionId: String,
    override val schemaVersion: String,
    override val version: String,
    val view: String,
    val total: Int,
    val items: List<GearMobileDto>,
    val measurements: List<GearMeasurementDto> = emptyList(),
    val context: GearsContextDto? = null,
    val offset: Int? = null,
    val limit: Int? = null,
) : ReferenceDataEnvelopeMetadata

/**
 * One entry in [ManifestDto.datasets]. `map-land`/`map-statistical-areas` entries (and any future unknown
 * dataset name) parse the same shape — callers simply ignore any `dataset` they do not recognise.
 */
@Serializable
internal data class ManifestDatasetDto(
    val dataset: String,
    val collectionId: String,
    val schemaVersion: String,
    val version: String,
    val etag: String,
    val url: String,
    val format: String,
    val itemCount: Int,
    val sizeBytes: Long? = null,
    val lastModified: String? = null,
)

@Serializable
internal data class ManifestDto(
    val manifestId: String,
    val version: String,
    val generatedAt: String? = null,
    val datasets: List<ManifestDatasetDto> = emptyList(),
)

@Serializable
internal data class ErrorDetailDto(
    val path: String? = null,
    val message: String? = null,
)

/** The `error` object inside [ErrorEnvelopeDto] — see ADR 0015 and the service's error-envelope docs. */
@Serializable
internal data class ErrorBodyDto(
    val code: String,
    val message: String,
    val traceId: String? = null,
    val dataset: String? = null,
    val retryable: Boolean? = null,
    val details: List<ErrorDetailDto>? = null,
)

@Serializable
internal data class ErrorEnvelopeDto(
    val error: ErrorBodyDto,
)
