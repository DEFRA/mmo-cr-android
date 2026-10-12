package uk.gov.defra.mmocatchrecord.feature.home.presentation

import uk.gov.defra.mmocatchrecord.common.design.RecordStatusTag
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus

/** Maps a persisted [DraftStatus] to its Home-screen display tag; `Discarded` drafts are list-filtered. */
fun DraftStatus.toRecordStatusTag(): RecordStatusTag? =
    when (this) {
        DraftStatus.Draft -> RecordStatusTag.Draft
        DraftStatus.ReadyToSubmit -> RecordStatusTag.ReadyToSubmit
        DraftStatus.PendingSync -> RecordStatusTag.AwaitingSync
        DraftStatus.Submitted -> RecordStatusTag.Submitted
        DraftStatus.Discarded -> null
    }
