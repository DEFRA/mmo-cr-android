package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear

import uk.gov.defra.mmocatchrecord.common.design.GdsCheckboxOption
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.SpeciesWeightEntry
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Species
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.species.SpeciesWeightEntryValidationResult
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.species.SpeciesWeightFieldError
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.species.SpeciesWeightFieldKind
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.species.SpeciesWeightSupport
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.species.SpeciesWeightValidator

/** Pure state seeding and save-time validation for [GearSpeciesChecklistScreenContent]. */
internal object GearSpeciesChecklistSupport {
    fun rawValueKey(
        speciesId: String,
        kind: SpeciesWeightFieldKind,
    ): String = "$speciesId::${kind.name}"

    /** Seeds/re-derives the weight-field text inputs from [gearUse] (BR-XX dirty-tracking baseline). */
    fun rawValuesFor(gearUse: GearUse): Map<String, String> =
        gearUse.speciesWeights
            .flatMap { entry ->
                listOfNotNull(
                    entry.weightAboveMinimumSizeKg?.let {
                        rawValueKey(entry.speciesId, SpeciesWeightFieldKind.AboveMinimumSize) to
                            GearMeasurementSupport.formatNumber(it)
                    },
                    entry.weightBelowMinimumSizeKg?.let {
                        rawValueKey(entry.speciesId, SpeciesWeightFieldKind.BelowMinimumSize) to
                            GearMeasurementSupport.formatNumber(it)
                    },
                    entry.weightLegallyDiscardedKg?.let {
                        rawValueKey(entry.speciesId, SpeciesWeightFieldKind.LegallyDiscarded) to
                            GearMeasurementSupport.formatNumber(it)
                    },
                )
            }.toMap()

    fun initialDisclosedKeysFor(gearUse: GearUse): Set<String> =
        gearUse.speciesWeights
            .flatMap { entry ->
                listOfNotNull(
                    entry.weightBelowMinimumSizeKg?.let {
                        rawValueKey(entry.speciesId, SpeciesWeightFieldKind.BelowMinimumSize)
                    },
                    entry.weightLegallyDiscardedKg?.let {
                        rawValueKey(entry.speciesId, SpeciesWeightFieldKind.LegallyDiscarded)
                    },
                )
            }.toSet()

    class ChecklistValidation(
        val entries: List<SpeciesWeightEntry>,
        val errors: Map<String, SpeciesWeightFieldError>,
    )

    @Suppress("LongParameterList")
    fun validateChecklist(
        draft: CatchRecordDraft,
        gearUse: GearUse,
        speciesList: List<Species>,
        checkedIds: Set<String>,
        rawValues: Map<String, String>,
        disclosed: Set<String>,
    ): ChecklistValidation {
        val validationErrors = mutableMapOf<String, SpeciesWeightFieldError>()
        val validatedEntries =
            gearUse.speciesWeights.map { entry ->
                val species =
                    speciesList.firstOrNull { it.id == entry.speciesId }?.takeIf { entry.speciesId in checkedIds }
                        ?: return@map entry
                val result = validateSpeciesRow(draft, gearUse, species, rawValues, disclosed)
                validationErrors += result.errors.mapKeys { (kind, _) -> rawValueKey(entry.speciesId, kind) }
                entry.copy(
                    confirmedCaught = true,
                    weightAboveMinimumSizeKg = result.values[SpeciesWeightFieldKind.AboveMinimumSize],
                    weightBelowMinimumSizeKg = result.values[SpeciesWeightFieldKind.BelowMinimumSize],
                    weightLegallyDiscardedKg = result.values[SpeciesWeightFieldKind.LegallyDiscarded],
                )
            }
        return ChecklistValidation(entries = validatedEntries, errors = validationErrors)
    }

    fun validateSpeciesRow(
        draft: CatchRecordDraft,
        gearUse: GearUse,
        species: Species,
        rawValues: Map<String, String>,
        disclosed: Set<String>,
    ): SpeciesWeightEntryValidationResult {
        val aboveKey = rawValueKey(species.id, SpeciesWeightFieldKind.AboveMinimumSize)
        val belowKey = rawValueKey(species.id, SpeciesWeightFieldKind.BelowMinimumSize)
        val discardedKey = rawValueKey(species.id, SpeciesWeightFieldKind.LegallyDiscarded)
        val rawValuesByField =
            buildMap {
                put(SpeciesWeightFieldKind.AboveMinimumSize, rawValues[aboveKey].orEmpty())
                if (belowKey in disclosed) put(SpeciesWeightFieldKind.BelowMinimumSize, rawValues[belowKey].orEmpty())
                if (discardedKey in
                    disclosed
                ) {
                    put(SpeciesWeightFieldKind.LegallyDiscarded, rawValues[discardedKey].orEmpty())
                }
            }
        return SpeciesWeightValidator.validateEntry(
            species = species,
            rawValuesByField = rawValuesByField,
            mandatoryFields =
                if (species.weightAboveMinimumSizeMandatory) {
                    setOf(SpeciesWeightFieldKind.AboveMinimumSize)
                } else {
                    emptySet()
                },
            cumulativeOtherEntriesKg =
                SpeciesWeightSupport.cumulativeWeightForSpeciesInOtherGearUses(draft, species.id, gearUse.id),
        )
    }

    fun replaceGearUse(
        draft: CatchRecordDraft,
        updatedGearUse: GearUse,
    ): CatchRecordDraft =
        draft.copy(gearUses = draft.gearUses.map { if (it.id == updatedGearUse.id) updatedGearUse else it })

    /** Maps [gearUse]'s species weights to checkbox options, resolving each row's display label. */
    fun checkboxOptionsFor(
        gearUse: GearUse,
        speciesList: List<Species>,
    ): List<GdsCheckboxOption> =
        gearUse.speciesWeights.map { entry ->
            val species = speciesList.firstOrNull { it.id == entry.speciesId }
            GdsCheckboxOption(id = entry.speciesId, label = species?.name ?: entry.speciesId)
        }
}
