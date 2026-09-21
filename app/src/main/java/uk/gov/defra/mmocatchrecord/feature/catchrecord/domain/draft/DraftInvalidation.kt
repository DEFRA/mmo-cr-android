package uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft

/**
 * Pure (no Android/Compose/Room dependency), directly unit-testable FR10 "dependent-data invalidation"
 * rules for the catch-record draft aggregate — see ADR 0007 ("FR10 dependent-data invalidation is modelled
 * explicitly in the reducer") and ADR 0006 ("changing a stat-rectangle selection invalidates dependent
 * species/weight rows").
 *
 * [reconcile] is the single entry point, called by [uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowViewModel]
 * (the wizard's reducer) immediately before every persisted draft mutation, so dependent-data invalidation
 * is enforced centrally — regardless of which screen/event produced the naive next draft — rather than
 * being re-implemented (or forgotten) per screen.
 */
object DraftInvalidation {
    /**
     * Reconciles [next] (a caller-proposed next draft state) against [previous] (the last persisted state,
     * or `null` for a brand-new draft) by clearing any data that is no longer valid given what changed:
     *
     * - A gear use that was confirmed and is no longer confirmed ("unconfirmed") has its statistical
     *   sub-rectangle and species/weight entries cleared — they only make sense for gear confirmed as used.
     * - A gear use whose statistical sub-rectangle **changed** (was already set, and is now set to a
     *   different value — i.e. an edit, not the first-time set) has its species/weight entries cleared,
     *   since they were captured against the previous rectangle (ADR 0006).
     * - A gear use removed entirely between [previous] and [next] needs no special handling here: it is
     *   simply absent from `next.gearUses`, and Room's `onDelete = CASCADE` foreign keys remove its
     *   persisted child rows.
     * - Finally, any trip-level "not landing straight away" species entry ([CatchRecordDraft.notLandedSpeciesEntries])
     *   whose species is no longer confirmed-caught by *any* remaining confirmed gear use is pruned, and the
     *   whole list is cleared outright if [CatchRecordDraft.notLandedStraightAway] is not (or no longer) `true`.
     */
    fun reconcile(
        previous: CatchRecordDraft?,
        next: CatchRecordDraft,
    ): CatchRecordDraft {
        val reconciledGearUses = next.gearUses.map { gearUse -> reconcileGearUse(previous, gearUse) }
        return pruneOrphanNotLandedSpecies(next.copy(gearUses = reconciledGearUses))
    }

    private fun reconcileGearUse(
        previous: CatchRecordDraft?,
        gearUse: GearUse,
    ): GearUse {
        val previousGearUse = previous?.gearUses?.firstOrNull { it.id == gearUse.id } ?: return gearUse

        val wasUnconfirmed = previousGearUse.confirmedUsedOnTrip && !gearUse.confirmedUsedOnTrip
        if (wasUnconfirmed) {
            return gearUse.copy(statisticalSubRectangleCode = null, speciesWeights = emptyList())
        }

        val statRectangleChanged =
            previousGearUse.statisticalSubRectangleCode != null &&
                gearUse.statisticalSubRectangleCode != null &&
                previousGearUse.statisticalSubRectangleCode != gearUse.statisticalSubRectangleCode
        if (statRectangleChanged) {
            return gearUse.copy(speciesWeights = emptyList())
        }

        return gearUse
    }

    private fun pruneOrphanNotLandedSpecies(draft: CatchRecordDraft): CatchRecordDraft {
        if (draft.notLandedStraightAway != true) {
            return if (draft.notLandedSpeciesEntries.isEmpty()) draft else draft.copy(notLandedSpeciesEntries = emptyList())
        }

        val confirmedSpeciesIds =
            draft.gearUses
                .filter { it.confirmedUsedOnTrip }
                .flatMap { it.speciesWeights }
                .filter { it.confirmedCaught }
                .map { it.speciesId }
                .toSet()
        val prunedEntries = draft.notLandedSpeciesEntries.filter { it.speciesId in confirmedSpeciesIds }
        return if (prunedEntries.size == draft.notLandedSpeciesEntries.size) draft else draft.copy(notLandedSpeciesEntries = prunedEntries)
    }
}
