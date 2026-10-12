package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local

/** Flat list projection for the Home records list — not the full aggregate; see ADR 0014. */
data class DraftSummaryRow(
    val id: String,
    val vesselId: String,
    val catchRecordReference: String?,
    val status: String,
    val returnDay: Int?,
    val returnMonth: Int?,
    val returnYear: Int?,
    val createdAtEpochMillis: Long?,
    val modifiedAtEpochMillis: Long,
    val submittedAtEpochMillis: Long?,
    val syncedAtEpochMillis: Long?,
)
