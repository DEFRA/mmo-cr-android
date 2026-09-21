package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.species

import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear.StatisticalSubRectangleFormatValidator
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear.StatisticalSubRectangleInputError

/** Errors for the gear-species search field (Phase 5A, screen 1) — mirrors [StatisticalSubRectangleInputError]. */
enum class SpeciesSearchInputError {
    /** Nothing typed at all when "Save and continue" was pressed. */
    EmptyQuery,

    /** Something was typed but it doesn't match a suggestion/known species exactly. */
    NoValidSelection,
}

/**
 * Pure validator for the gear-species search screen's "Save and continue": mirrors the two-variant
 * free-text-vs-list pattern used by [StatisticalSubRectangleFormatValidator]'s required check, but here
 * both variants are simple "did the user pick a real species" checks (no format rule — species are chosen
 * from a known reference-data list only, there is no free-text "Other" path for species).
 */
object SpeciesSearchValidator {
    fun validate(
        query: String,
        selectedSpeciesId: String?,
    ): SpeciesSearchInputError? =
        when {
            selectedSpeciesId != null -> null
            query.isBlank() -> SpeciesSearchInputError.EmptyQuery
            else -> SpeciesSearchInputError.NoValidSelection
        }
}
