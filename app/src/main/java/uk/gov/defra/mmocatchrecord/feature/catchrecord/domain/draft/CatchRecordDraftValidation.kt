package uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft

/** A single, specific reason [CatchRecordDraftValidation.validateForSubmission] rejected a draft. */
enum class DraftSubmissionIssue {
    /** [CatchRecordDraft.isTripToday] is unanswered, or the resulting departure/return dates are missing. */
    TripTimingIncomplete,

    /** Either [CatchRecordDraft.departurePort] or [CatchRecordDraft.returnPort] is missing. */
    PortsIncomplete,

    /** No [GearUse] has [GearUse.confirmedUsedOnTrip] `true` — nothing was actually recorded as used. */
    NoConfirmedGear,

    /** A confirmed gear use has no [GearUse.statisticalSubRectangleCode] recorded. */
    GearMissingStatisticalSubRectangle,

    /** A confirmed gear use has no [SpeciesWeightEntry] with [SpeciesWeightEntry.confirmedCaught] `true`. */
    GearMissingConfirmedSpecies,

    /** [CatchRecordDraft.notLandedStraightAway] has not been answered. */
    NotLandedDecisionUnanswered,

    /** [CatchRecordDraft.notLandedStraightAway] is `true` but [CatchRecordDraft.notLandedSpeciesEntries] is empty. */
    NotLandedSpeciesIncomplete,

    /** [CatchRecordDraft.status] is already terminal ([DraftStatus.Submitted]/[DraftStatus.Discarded]) — this draft must never be (re-)submitted. */
    DraftNotInSubmittableStatus,
}

/** Result of [CatchRecordDraftValidation.validateForSubmission]. */
sealed interface DraftSubmissionValidation {
    data object Valid : DraftSubmissionValidation

    data class Invalid(
        val issues: List<DraftSubmissionIssue>,
    ) : DraftSubmissionValidation
}

/**
 * Pure (no Android dependency), directly unit-testable aggregate validation enforced at the submission
 * boundary — see [uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordFlowViewModel.acceptDeclarationAndSubmit]
 * and `CatchRecordSyncWorker`.
 *
 * This exists so an incomplete draft can **never** be submitted, even if it somehow reaches the submission
 * boundary via a corrupted/persisted state or a direct event dispatch that bypassed the wizard's normal
 * step-by-step gating (e.g. a future bug, a manually-crafted test, or a future direct-deeplink entry point) —
 * the wizard's own [uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.nextWizardStepForDraft]
 * step-derivation is a *routing* concern for the normal happy path, not a submission gate in its own right.
 *
 * Deliberately callable for a [DraftStatus.PendingSync] draft too (not just [DraftStatus.Draft]/
 * [DraftStatus.ReadyToSubmit]): `CatchRecordSyncWorker`'s background retry re-validates the *same* draft
 * data before resubmitting it, so only [DraftStatus.Submitted]/[DraftStatus.Discarded] (truly terminal,
 * already-final states) are rejected by [DraftSubmissionIssue.DraftNotInSubmittableStatus].
 */
object CatchRecordDraftValidation {
    fun validateForSubmission(draft: CatchRecordDraft): DraftSubmissionValidation {
        val issues = mutableListOf<DraftSubmissionIssue>()

        if (draft.status == DraftStatus.Submitted || draft.status == DraftStatus.Discarded) {
            issues += DraftSubmissionIssue.DraftNotInSubmittableStatus
        }
        if (draft.isTripToday == null || draft.departureDate == null || draft.returnDate == null) {
            issues += DraftSubmissionIssue.TripTimingIncomplete
        }
        if (draft.departurePort == null || draft.returnPort == null) {
            issues += DraftSubmissionIssue.PortsIncomplete
        }

        val confirmedGearUses = draft.gearUses.filter { it.confirmedUsedOnTrip }
        if (confirmedGearUses.isEmpty()) {
            issues += DraftSubmissionIssue.NoConfirmedGear
        } else {
            if (confirmedGearUses.any { it.statisticalSubRectangleCode == null }) {
                issues += DraftSubmissionIssue.GearMissingStatisticalSubRectangle
            }
            if (confirmedGearUses.any { gearUse -> gearUse.speciesWeights.none { it.confirmedCaught } }) {
                issues += DraftSubmissionIssue.GearMissingConfirmedSpecies
            }
            if (draft.notLandedStraightAway == null) {
                issues += DraftSubmissionIssue.NotLandedDecisionUnanswered
            }
            if (draft.notLandedStraightAway == true && draft.notLandedSpeciesEntries.isEmpty()) {
                issues += DraftSubmissionIssue.NotLandedSpeciesIncomplete
            }
        }

        return if (issues.isEmpty()) DraftSubmissionValidation.Valid else DraftSubmissionValidation.Invalid(issues)
    }
}
