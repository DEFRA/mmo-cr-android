package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// Shared ref/snapshot shapes (write and read-side both carry snapshots per ADR 0026's backend answers).

@Serializable
internal data class CreateDraftCatchRecordPayloadDto(
    val vesselId: String,
    val catchRecordReference: String? = null,
)

@Serializable
internal data class VesselRefDto(
    val id: String,
)

@Serializable
internal data class PortRefDto(
    val id: String,
    val codeSnapshot: String,
    val nameSnapshot: String,
)

@Serializable
internal data class GearRefDto(
    val id: String,
    val codeSnapshot: String,
    val nameSnapshot: String,
)

@Serializable
internal data class StatisticalAreaDto(
    val id: String,
    val codeSnapshot: String,
    val nameSnapshot: String,
)

@Serializable
internal data class TripDto(
    val startedAndFinishedToday: Boolean,
    val departurePort: PortRefDto,
    val returnPort: PortRefDto,
    val dateStarted: String? = null,
    val dateEnded: String? = null,
)

/** `pairVessel`/`pairSkipperName` are always sent explicitly (even when `null`) — no default. */
@Serializable
internal data class PairFishingDto(
    val enabled: Boolean,
    val pairVessel: String?,
    val pairSkipperName: String?,
)

@Serializable
internal data class CharacteristicDto(
    val characteristicId: String,
    val nameSnapshot: String,
    val value: JsonElement,
    val unitSnapshot: String? = null,
)

/** Unset weights are omitted, not sent as explicit `null` (ADR 0026) — every weight field defaults `null`. */
@Serializable
internal data class SpeciesWeightEntryDto(
    val id: String,
    val faoCodeSnapshot: String,
    val nameSnapshot: String,
    val weightAboveMinimumKg: Double? = null,
    val weightBelowMinimumKg: Double? = null,
    val weightLegallyDiscardedKg: Double? = null,
    val weightPrecision: String? = null,
)

@Serializable
internal data class GearAssociationDto(
    val gear: GearRefDto,
    val statisticalArea: StatisticalAreaDto,
    val characteristics: List<CharacteristicDto>,
    val speciesCaught: List<SpeciesWeightEntryDto>,
    /** OQ-7: omitted by default (server generates it); flip to `GearUse.id` if a 422 names it (ADR 0026). */
    val associationId: String? = null,
)

@Serializable
internal data class CompleteReplacementPayloadDto(
    val vessel: VesselRefDto,
    val trip: TripDto,
    val pairFishing: PairFishingDto,
    val gears: List<GearAssociationDto>,
    val speciesNotLanded: List<SpeciesWeightEntryDto>,
)

// Response DTOs shared by the create/replace/submit save responses.

@Serializable
internal data class SectionCompletionDto(
    val trip: Boolean,
    val pairFishing: Boolean,
    val gears: Boolean,
)

@Serializable
internal data class ProgressDto(
    val hasUnsubmittedChanges: Boolean,
    val numberOfSubmissions: Int,
    val incompleteGearAssociationIds: List<String>,
    val currentIncompleteGearAssociationId: String?,
    val allGearsComplete: Boolean,
)

@Serializable
internal data class ArtifactMetadataDto(
    val submissionNumber: Int,
    val type: String,
)

@Serializable
internal data class StandardSaveResponseDto(
    val id: String,
    val catchRecordReference: String,
    val status: String,
    val displayStatus: String,
    val version: Int,
    val savedSection: String?,
    val sectionCompletion: SectionCompletionDto,
    val progress: ProgressDto,
)

/** Flattens the OpenAPI `allOf` of `StandardSaveResponse` + submission fields (ADR 0026). */
@Serializable
internal data class SubmissionResponseDto(
    val id: String,
    val catchRecordReference: String,
    val status: String,
    val displayStatus: String,
    val version: Int,
    val savedSection: String?,
    val sectionCompletion: SectionCompletionDto,
    val progress: ProgressDto,
    val submittedAt: String,
    val submittedBy: String,
    val artifacts: List<ArtifactMetadataDto>,
)

/** Minimal recovery-GET shape (S4): only what conflict recovery needs, so a partial DRAFT record whose
 *  full canonical snapshot fields aren't all populated yet still decodes (`ignoreUnknownKeys` is on). */
@Serializable
internal data class CatchRecordRecoverySnapshotDto(
    val id: String,
    val catchRecordReference: String? = null,
    val status: String,
    val version: Int,
    val hasUnsubmittedChanges: Boolean = false,
)

// Read-side (recovery GET, OQ-1) DTOs — the full Canonical Catch Record snapshot shape.

@Serializable
internal data class VesselSnapshotDto(
    val id: String,
    val rssSnapshot: String,
    val nameSnapshot: String,
    val externalMarkSnapshot: String,
    val lengthOverallMetresSnapshot: Double? = null,
)

@Serializable
internal data class TripSnapshotDto(
    val startedAndFinishedToday: Boolean,
    val dateStarted: String,
    val dateEnded: String,
    val departurePort: PortRefDto,
    val returnPort: PortRefDto,
)

@Serializable
internal data class GearAssociationSnapshotDto(
    val associationId: String,
    val gear: GearRefDto,
    val characteristics: List<CharacteristicDto>,
    val statisticalArea: StatisticalAreaDto? = null,
    val speciesCaught: List<SpeciesWeightEntryDto>,
)

/** Flattens the OpenAPI `allOf` of `CatchRecord` + the derived retrieval-only fields (ADR 0026). */
@Serializable
internal data class RetrieveCatchRecordResponseDto(
    val schemaVersion: Int,
    val id: String,
    val catchRecordReference: String,
    val ownerUserId: String,
    val status: String,
    val version: Int,
    val numberOfSubmissions: Int,
    val hasUnsubmittedChanges: Boolean,
    val vessel: VesselSnapshotDto,
    val trip: TripSnapshotDto,
    val pairFishing: PairFishingDto,
    val gears: List<GearAssociationSnapshotDto>,
    val speciesNotLanded: List<SpeciesWeightEntryDto>,
    val artifacts: List<ArtifactMetadataDto>,
    val createdAt: String,
    val createdBy: String,
    val updatedAt: String,
    val updatedBy: String,
    val submittedAt: String?,
    val submittedBy: String?,
    val completedAt: String?,
    val completedBy: String?,
    val displayStatus: String,
    val sectionCompletion: SectionCompletionDto,
    val completedSections: List<String>,
    val incompleteSections: List<String>,
    val progress: ProgressDto,
    val submissionEligible: Boolean,
)

@Serializable
internal data class CatchRecordListingVesselDto(
    val nameSnapshot: String? = null,
    val rssSnapshot: String? = null,
)

@Serializable
internal data class CatchRecordListingTripDto(
    val dateStarted: String? = null,
    val dateEnded: String? = null,
)

@Serializable
internal data class CatchRecordListingProgressDto(
    val allGearsComplete: Boolean? = null,
)

@Serializable
internal data class CatchRecordListingItemDto(
    val id: String,
    val catchRecordReference: String,
    val status: String,
    val displayStatus: String,
    val version: Int,
    val vessel: CatchRecordListingVesselDto,
    val trip: CatchRecordListingTripDto,
    val createdAt: String,
    val updatedAt: String,
    val submittedAt: String?,
    val completedAt: String?,
    val progress: CatchRecordListingProgressDto,
)

@Serializable
internal data class CatchRecordListResponseDto(
    val items: List<CatchRecordListingItemDto>,
    val limit: Int,
    val count: Int,
)

// Error DTOs — a flat `{statusCode, code, message, correlationId?, details?}` shape, unlike the
// reference-data service's nested `{"error": {...}}` envelope (ADR 0026).

@Serializable
internal data class ErrorDetailDto(
    val path: String? = null,
    val code: String? = null,
    val message: String? = null,
    val min: Double? = null,
    val max: Double? = null,
    val format: String? = null,
    val allowedValues: List<JsonElement>? = null,
)

@Serializable
internal data class ErrorResponseDto(
    val statusCode: Int,
    val code: String,
    val message: String,
    val correlationId: String? = null,
    val details: List<ErrorDetailDto>? = null,
)
