package uk.gov.defra.mmocatchrecord.common.design

import androidx.annotation.StringRes
import uk.gov.defra.mmocatchrecord.R

/** User-facing catch record statuses per the status management rules; see ADR 0014 for placement. */
enum class RecordStatusTag(
    @param:StringRes val labelRes: Int,
) {
    Draft(R.string.status_draft),
    CompletePending(R.string.status_complete_pending),
    Submitted(R.string.status_submitted),

    // UI-managed only (not persisted by the backend); not yet reachable until an amend flow exists.
    Amended(R.string.status_amended),
}
