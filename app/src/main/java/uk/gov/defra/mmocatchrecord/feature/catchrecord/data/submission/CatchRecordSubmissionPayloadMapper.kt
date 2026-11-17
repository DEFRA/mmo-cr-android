package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission

import kotlinx.serialization.json.JsonPrimitive
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote.CharacteristicDto
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote.CompleteReplacementPayloadDto
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote.CreateDraftCatchRecordPayloadDto
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote.GearAssociationDto
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote.GearRefDto
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote.PairFishingDto
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote.PortRefDto
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote.SpeciesWeightEntryDto
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote.StatisticalAreaDto
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote.TripDto
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote.VesselRefDto
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DmyDate
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.MeasurementValue
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.SpeciesWeightEntry
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.SubmissionMappingException
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.UnresolvedGearMeasurementException
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Port
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Species
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.SpeciesWeightPrecision
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.StatisticalSubRectangle
import java.util.Locale

/** Maps a [CatchRecordDraft] + its resolved reference data to the wire payloads (ADR 0026); unresolvable
 * lookups throw a terminal [SubmissionMappingException] — see [SEND_ASSOCIATION_ID] for OQ-7. */
internal object CatchRecordSubmissionPayloadMapper {
    private const val SEND_ASSOCIATION_ID = false

    fun toCreatePayload(draft: CatchRecordDraft): CreateDraftCatchRecordPayloadDto =
        CreateDraftCatchRecordPayloadDto(vesselId = draft.vesselId, catchRecordReference = draft.catchRecordReference)

    fun toReplacePayload(
        draft: CatchRecordDraft,
        ports: List<Port>,
        gearTypes: List<GearType>,
        species: List<Species>,
        statisticalSubRectangles: List<StatisticalSubRectangle>,
    ): CompleteReplacementPayloadDto {
        val portsById = ports.associateBy { it.id }
        val gearTypesById = gearTypes.associateBy { it.id }
        val speciesById = species.associateBy { it.id }
        val rectanglesByCode = statisticalSubRectangles.associateBy { it.code }

        return CompleteReplacementPayloadDto(
            vessel = VesselRefDto(draft.vesselId),
            trip = toTrip(draft, portsById),
            pairFishing = PairFishingDto(enabled = false, pairVessel = null, pairSkipperName = null),
            gears =
                draft.gearUses
                    .filter { it.confirmedUsedOnTrip }
                    .map { toGearAssociation(it, gearTypesById, speciesById, rectanglesByCode) },
            speciesNotLanded =
                draft.notLandedSpeciesEntries.map { entry ->
                    val species =
                        speciesById[entry.speciesId]
                            ?: throw SubmissionMappingException("Unresolvable species id '${entry.speciesId}'")
                    toSpeciesWeightEntryDto(
                        species = species,
                        weightAboveMinimumKg = entry.weightAboveMinimumSizeKeptOnboardKg,
                        weightBelowMinimumKg = null,
                        weightLegallyDiscardedKg = null,
                    )
                },
        )
    }

    private fun toTrip(
        draft: CatchRecordDraft,
        portsById: Map<String, Port>,
    ): TripDto {
        val startedAndFinishedToday =
            draft.isTripToday ?: throw SubmissionMappingException("Draft has no isTripToday answer")
        return TripDto(
            startedAndFinishedToday = startedAndFinishedToday,
            departurePort = toPortRef(draft.departurePort?.portId, portsById, "departure"),
            returnPort = toPortRef(draft.returnPort?.portId, portsById, "return"),
            dateStarted = if (startedAndFinishedToday) null else toIsoDate(draft.departureDate, "departure"),
            dateEnded = if (startedAndFinishedToday) null else toIsoDate(draft.returnDate, "return"),
        )
    }

    private fun toPortRef(
        portId: String?,
        portsById: Map<String, Port>,
        label: String,
    ): PortRefDto {
        val port =
            portId?.let(portsById::get)?.takeIf { it.code.isNotBlank() }
                ?: throw SubmissionMappingException("Draft has no resolvable $label port")
        return PortRefDto(id = port.id, codeSnapshot = port.code, nameSnapshot = port.name)
    }

    private fun toIsoDate(
        date: DmyDate?,
        label: String,
    ): String {
        val value = date ?: throw SubmissionMappingException("Draft has no $label date")
        return "%04d-%02d-%02d".format(Locale.ROOT, value.year, value.month, value.day)
    }

    private fun toGearAssociation(
        gearUse: GearUse,
        gearTypesById: Map<String, GearType>,
        speciesById: Map<String, Species>,
        rectanglesByCode: Map<String, StatisticalSubRectangle>,
    ): GearAssociationDto {
        val gearType =
            gearTypesById[gearUse.gearTypeId]
                ?: throw SubmissionMappingException("Unresolvable gear type id '${gearUse.gearTypeId}'")
        if (gearType.code.isBlank()) {
            throw SubmissionMappingException("Gear type '${gearUse.gearTypeId}' has no reference-data code")
        }
        return GearAssociationDto(
            gear = GearRefDto(id = gearType.id, codeSnapshot = gearType.code, nameSnapshot = gearType.name),
            statisticalArea = toStatisticalArea(gearUse, rectanglesByCode),
            characteristics = gearUse.measurements.map { (key, value) -> toCharacteristic(key, value, gearType) },
            speciesCaught =
                gearUse.speciesWeights.filter { it.confirmedCaught }.map { toSpeciesWeight(it, speciesById) },
            associationId = if (SEND_ASSOCIATION_ID) gearUse.id else null,
        )
    }

    private fun toStatisticalArea(
        gearUse: GearUse,
        rectanglesByCode: Map<String, StatisticalSubRectangle>,
    ): StatisticalAreaDto {
        val code = gearUse.statisticalSubRectangleCode
        val rectangle =
            code?.let(rectanglesByCode::get)?.takeIf { it.id.isNotBlank() }
                ?: throw SubmissionMappingException("Unresolvable statistical sub-rectangle for gear '${gearUse.id}'")
        val nameSnapshot =
            rectangle.icesRectangleName?.takeIf { it.isNotBlank() }
                ?: throw SubmissionMappingException("Sub-rectangle '${rectangle.code}' has no reference-data name")
        return StatisticalAreaDto(id = rectangle.id, codeSnapshot = rectangle.code, nameSnapshot = nameSnapshot)
    }

    private fun toCharacteristic(
        key: String,
        value: MeasurementValue,
        gearType: GearType,
    ): CharacteristicDto {
        val field =
            gearType.measurementFields.firstOrNull { it.key == key }
                ?: throw SubmissionMappingException("Unresolvable gear measurement key '$key' for '${gearType.id}'")
        if (field.id.isBlank()) {
            throw UnresolvedGearMeasurementException("Gear measurement '$key' has no reference-data characteristic id")
        }
        val jsonValue =
            when (value) {
                is MeasurementValue.Numeric -> JsonPrimitive(value.value)
                is MeasurementValue.Text -> JsonPrimitive(value.value)
            }
        return CharacteristicDto(
            characteristicId = field.id,
            nameSnapshot = field.label,
            value = jsonValue,
            unitSnapshot = field.unit?.takeIf { it.isNotBlank() },
        )
    }

    private fun toSpeciesWeight(
        entry: SpeciesWeightEntry,
        speciesById: Map<String, Species>,
    ): SpeciesWeightEntryDto {
        val species =
            speciesById[entry.speciesId]
                ?: throw SubmissionMappingException("Unresolvable species id '${entry.speciesId}'")
        return toSpeciesWeightEntryDto(
            species = species,
            weightAboveMinimumKg = entry.weightAboveMinimumSizeKg,
            weightBelowMinimumKg = entry.weightBelowMinimumSizeKg,
            weightLegallyDiscardedKg = entry.weightLegallyDiscardedKg,
        )
    }

    private fun toSpeciesWeightEntryDto(
        species: Species,
        weightAboveMinimumKg: Double?,
        weightBelowMinimumKg: Double?,
        weightLegallyDiscardedKg: Double?,
    ): SpeciesWeightEntryDto =
        SpeciesWeightEntryDto(
            id = species.id,
            faoCodeSnapshot = species.faoCode,
            nameSnapshot = species.name,
            weightAboveMinimumKg = weightAboveMinimumKg,
            weightBelowMinimumKg = weightBelowMinimumKg,
            weightLegallyDiscardedKg = weightLegallyDiscardedKg,
            weightPrecision =
                when (species.weightPrecision) {
                    SpeciesWeightPrecision.WholeNumber -> "wholeNumber"
                    SpeciesWeightPrecision.OneDecimalPlace -> "oneDecimalPlace"
                },
        )
}
