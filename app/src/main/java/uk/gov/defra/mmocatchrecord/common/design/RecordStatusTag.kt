package uk.gov.defra.mmocatchrecord.common.design

import androidx.annotation.StringRes
import uk.gov.defra.mmocatchrecord.R

/**
 * The four user-facing statuses a catch record can display (CRAR-152 Phase A) — see ADR 0014 for why this
 * replaces the old `CatchRecordStatus` and lives in `common.design`, not `feature.home.domain`.
 */
enum class RecordStatusTag(
    @param:StringRes val labelRes: Int,
) {
    Draft(R.string.status_draft),
    ReadyToSubmit(R.string.status_ready_to_submit),
    AwaitingSync(R.string.status_awaiting_sync),
    Submitted(R.string.status_submitted),
}
