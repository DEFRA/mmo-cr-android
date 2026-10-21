package uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft

/**
 * Builds a brand-new [CatchRecordDraft] candidate for a freshly selected vessel. Pure/no I/O so it is
 * trivially unit-testable; the caller is responsible for persisting it (see
 * `CatchRecordFlowViewModel.startNewDraft`, which now persists immediately on vessel selection — this
 * supersedes ADR 0014's original BR-XX deferral to the next "Save and continue").
 */
object CatchRecordDraftFactory {
    fun newDraft(
        vesselId: String,
        id: String,
        reference: String,
    ): CatchRecordDraft =
        CatchRecordDraft(
            id = id,
            vesselId = vesselId,
            status = DraftStatus.Draft,
            modifiedAtEpochMillis = 0L,
            catchRecordReference = reference,
        )
}
