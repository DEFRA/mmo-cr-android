package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.species

import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Species
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear.GearMeasurementSupport

/**
 * Pure (no Android/Compose dependency) helpers shared by the gear-species search/checklist and Phase 5B
 * screens — mirrors [GearMeasurementSupport.displayNameFor].
 */
object SpeciesSupport {
    /**
     * Strips any parenthetical FAO-code suffix from a species' reference-data name (e.g. "Atlantic cod
     * (COD)" -> "Atlantic cod"), for use in validation/error messages that name the species in plain text.
     */
    fun displayNameFor(species: Species): String = species.name.substringBefore("(").trim()
}
