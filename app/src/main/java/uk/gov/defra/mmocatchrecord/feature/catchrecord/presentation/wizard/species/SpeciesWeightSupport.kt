package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.species

import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.SpeciesWeightEntry

/**
 * Pure (no Android/Compose dependency) helpers for computing cross-draft species totals — feeds
 * [SpeciesWeightValidator]'s quota check and the Phase 5B species-deduplication step.
 */
object SpeciesWeightSupport {
    /**
     * The cumulative already-saved weight (summing [SpeciesWeightEntry.weightAboveMinimumSizeKg]/
     * [SpeciesWeightEntry.weightBelowMinimumSizeKg]/[SpeciesWeightEntry.weightLegallyDiscardedKg]) for
     * [speciesId] across every gear use in [draft] *other than* [excludingGearUseId] — i.e. the baseline a
     * candidate new/edited weight for that one gear use's entry should be added to before checking it
     * against the species' quota. Deliberately excludes Phase 5B's "kept onboard" field — the confirmed
     * quota rule sums only the three Phase 5A fields.
     */
    fun cumulativeWeightForSpeciesInOtherGearUses(
        draft: CatchRecordDraft,
        speciesId: String,
        excludingGearUseId: String,
    ): Double =
        draft.gearUses
            .filter { it.id != excludingGearUseId }
            .flatMap { it.speciesWeights }
            .filter { it.speciesId == speciesId }
            .sumOf { entry ->
                (entry.weightAboveMinimumSizeKg ?: 0.0) +
                    (entry.weightBelowMinimumSizeKg ?: 0.0) +
                    (entry.weightLegallyDiscardedKg ?: 0.0)
            }

    /**
     * Every distinct species id confirmed-caught (see [SpeciesWeightEntry.confirmedCaught]) anywhere across
     * [draft]'s gear uses, in first-seen order — feeds Phase 5B screen 4's checklist.
     */
    fun distinctConfirmedSpeciesIds(draft: CatchRecordDraft): List<String> =
        draft.gearUses
            .flatMap { it.speciesWeights }
            .filter { it.confirmedCaught }
            .map { it.speciesId }
            .distinct()
}
