package uk.gov.defra.mmocatchrecord.common.design

/**
 * The four user-facing statuses a catch record can display (CRAR-152 Phase A) — see ADR 0014 for why this
 * replaces the old `CatchRecordStatus` and lives in `common.design`, not `feature.home.domain`.
 */
enum class RecordStatusTag {
    Draft,
    ReadyToSubmit,
    AwaitingSync,
    Submitted,
}
