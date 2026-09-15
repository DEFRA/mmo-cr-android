package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local

import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DmyDate
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.LandingStorageEntry
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.MeasurementValue
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.NotLandedSpeciesEntry
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelection
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelectionMode
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.SpeciesWeightEntry

/** Maps between the [CatchRecordDraft] domain aggregate and its Room entity representation. */
object DraftMappers {
    fun toEntities(draft: CatchRecordDraft): DraftAggregateEntities = DraftToEntityMapper.map(draft)

    fun toDomain(entity: DraftWithChildren): CatchRecordDraft = EntityToDraftMapper.map(entity)
}

private object DraftToEntityMapper {
    fun map(draft: CatchRecordDraft): DraftAggregateEntities {
        val draftEntity = toDraftEntity(draft)
        val gearUseEntities = mutableListOf<GearUseEntity>()
        val measurementEntities = mutableListOf<MeasurementEntity>()
        val speciesWeightEntities = mutableListOf<SpeciesWeightEntity>()

        draft.gearUses.forEachIndexed { index, gearUse ->
            gearUseEntities += toGearUseEntity(draft.id, gearUse, index)
            measurementEntities += toMeasurementEntities(gearUse)
            speciesWeightEntities += toSpeciesWeightEntities(gearUse)
        }

        return DraftAggregateEntities(
            draft = draftEntity,
            gearUses = gearUseEntities,
            measurements = measurementEntities,
            speciesWeights = speciesWeightEntities,
            landingStorage = toLandingStorageEntities(draft),
            notLandedSpecies = toNotLandedSpeciesEntities(draft),
        )
    }

    private fun toDraftEntity(draft: CatchRecordDraft): DraftEntity =
        DraftEntity(
            id = draft.id,
            vesselId = draft.vesselId,
            isTripToday = draft.isTripToday,
            departureDay = draft.departureDate?.day,
            departureMonth = draft.departureDate?.month,
            departureYear = draft.departureDate?.year,
            returnDay = draft.returnDate?.day,
            returnMonth = draft.returnDate?.month,
            returnYear = draft.returnDate?.year,
            departurePortId = draft.departurePort?.portId,
            departurePortSelectionMode = draft.departurePort?.selectionMode?.name,
            returnPortId = draft.returnPort?.portId,
            returnPortSelectionMode = draft.returnPort?.selectionMode?.name,
            status = draft.status.name,
            modifiedAtEpochMillis = draft.modifiedAtEpochMillis,
            notLandedStraightAway = draft.notLandedStraightAway,
        )

    private fun toGearUseEntity(
        draftId: String,
        gearUse: GearUse,
        orderIndex: Int,
    ): GearUseEntity =
        GearUseEntity(
            id = gearUse.id,
            draftId = draftId,
            gearTypeId = gearUse.gearTypeId,
            statisticalSubRectangleCode = gearUse.statisticalSubRectangleCode,
            orderIndex = orderIndex,
            numberOfShots = gearUse.numberOfShots,
            confirmedUsedOnTrip = gearUse.confirmedUsedOnTrip,
        )

    private fun toMeasurementEntities(gearUse: GearUse): List<MeasurementEntity> =
        gearUse.measurements.map { (key, value) ->
            when (value) {
                is MeasurementValue.Numeric ->
                    MeasurementEntity(
                        gearUseId = gearUse.id,
                        key = key,
                        numericValue = value.value,
                        unit = value.unit,
                        textValue = null,
                    )
                is MeasurementValue.Text ->
                    MeasurementEntity(
                        gearUseId = gearUse.id,
                        key = key,
                        numericValue = null,
                        unit = null,
                        textValue = value.value,
                    )
            }
        }

    private fun toSpeciesWeightEntities(gearUse: GearUse): List<SpeciesWeightEntity> =
        gearUse.speciesWeights.map { entry ->
            SpeciesWeightEntity(
                id = entry.id,
                gearUseId = gearUse.id,
                speciesId = entry.speciesId,
                weightAboveMinimumSizeKg = entry.weightAboveMinimumSizeKg,
                weightBelowMinimumSizeKg = entry.weightBelowMinimumSizeKg,
                weightLegallyDiscardedKg = entry.weightLegallyDiscardedKg,
                confirmedCaught = entry.confirmedCaught,
            )
        }

    private fun toLandingStorageEntities(draft: CatchRecordDraft): List<LandingStorageEntity> =
        draft.landingStorageEntries.flatMap { entry ->
            entry.fields.map { (key, value) ->
                LandingStorageEntity(draftId = draft.id, entryId = entry.id, key = key, value = value)
            }
        }

    private fun toNotLandedSpeciesEntities(draft: CatchRecordDraft): List<NotLandedSpeciesEntity> =
        draft.notLandedSpeciesEntries.map { entry ->
            NotLandedSpeciesEntity(
                draftId = draft.id,
                speciesId = entry.speciesId,
                weightAboveMinimumSizeKeptOnboardKg = entry.weightAboveMinimumSizeKeptOnboardKg,
            )
        }
}

private object EntityToDraftMapper {
    fun map(entity: DraftWithChildren): CatchRecordDraft {
        val draft = entity.draft

        return CatchRecordDraft(
            id = draft.id,
            vesselId = draft.vesselId,
            isTripToday = draft.isTripToday,
            departureDate = toDate(draft.departureDay, draft.departureMonth, draft.departureYear),
            returnDate = toDate(draft.returnDay, draft.returnMonth, draft.returnYear),
            departurePort = toPortSelection(draft.departurePortId, draft.departurePortSelectionMode),
            returnPort = toPortSelection(draft.returnPortId, draft.returnPortSelectionMode),
            gearUses = entity.gearUses.sortedBy { it.gearUse.orderIndex }.map(::toGearUse),
            landingStorageEntries = toLandingStorageEntries(entity.landingStorage),
            notLandedStraightAway = draft.notLandedStraightAway,
            notLandedSpeciesEntries = entity.notLandedSpecies.map(::toNotLandedSpeciesEntry),
            status = DraftStatus.valueOf(draft.status),
            modifiedAtEpochMillis = draft.modifiedAtEpochMillis,
        )
    }

    private fun toDate(
        day: Int?,
        month: Int?,
        year: Int?,
    ): DmyDate? = if (day != null && month != null && year != null) DmyDate(day, month, year) else null

    private fun toPortSelection(
        portId: String?,
        selectionMode: String?,
    ): PortSelection? = portId?.let { PortSelection(it, PortSelectionMode.valueOf(selectionMode!!)) }

    private fun toGearUse(withChildren: GearUseWithChildren): GearUse {
        val measurements =
            withChildren.measurements.associate { measurement ->
                val value: MeasurementValue =
                    if (measurement.numericValue != null) {
                        MeasurementValue.Numeric(measurement.numericValue, measurement.unit.orEmpty())
                    } else {
                        MeasurementValue.Text(measurement.textValue.orEmpty())
                    }
                measurement.key to value
            }
        return GearUse(
            id = withChildren.gearUse.id,
            gearTypeId = withChildren.gearUse.gearTypeId,
            statisticalSubRectangleCode = withChildren.gearUse.statisticalSubRectangleCode,
            measurements = measurements,
            speciesWeights =
                withChildren.speciesWeights.map { sw ->
                    SpeciesWeightEntry(
                        id = sw.id,
                        speciesId = sw.speciesId,
                        weightAboveMinimumSizeKg = sw.weightAboveMinimumSizeKg,
                        weightBelowMinimumSizeKg = sw.weightBelowMinimumSizeKg,
                        weightLegallyDiscardedKg = sw.weightLegallyDiscardedKg,
                        confirmedCaught = sw.confirmedCaught,
                    )
                },
            numberOfShots = withChildren.gearUse.numberOfShots,
            confirmedUsedOnTrip = withChildren.gearUse.confirmedUsedOnTrip,
        )
    }

    private fun toLandingStorageEntries(rows: List<LandingStorageEntity>): List<LandingStorageEntry> =
        rows
            .groupBy { it.entryId }
            .map { (entryId, entryRows) ->
                LandingStorageEntry(id = entryId, fields = entryRows.associate { it.key to it.value })
            }

    private fun toNotLandedSpeciesEntry(entity: NotLandedSpeciesEntity): NotLandedSpeciesEntry =
        NotLandedSpeciesEntry(
            speciesId = entity.speciesId,
            weightAboveMinimumSizeKeptOnboardKg = entity.weightAboveMinimumSizeKeptOnboardKg,
        )
}

/** Flattened set of entities produced from a [CatchRecordDraft] aggregate, ready for [CatchRecordDraftDao]. */
data class DraftAggregateEntities(
    val draft: DraftEntity,
    val gearUses: List<GearUseEntity>,
    val measurements: List<MeasurementEntity>,
    val speciesWeights: List<SpeciesWeightEntity>,
    val landingStorage: List<LandingStorageEntity>,
    val notLandedSpecies: List<NotLandedSpeciesEntity>,
)
