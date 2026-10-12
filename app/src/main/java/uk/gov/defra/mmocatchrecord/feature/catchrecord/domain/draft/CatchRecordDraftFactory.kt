package uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft

/**
 * Builds a brand-new, **unpersisted** [CatchRecordDraft] candidate (BR-XX: it only becomes a real Draft
 * once "Save and continue" is pressed — see ADR 0014). Pure/no I/O so it is trivially unit-testable.
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
