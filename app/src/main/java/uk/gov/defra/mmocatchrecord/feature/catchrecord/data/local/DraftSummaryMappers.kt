package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local

import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DmyDate
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import uk.gov.defra.mmocatchrecord.feature.home.domain.CatchRecordSummary
import uk.gov.defra.mmocatchrecord.feature.home.presentation.toRecordStatusTag

/** Maps [DraftSummaryRow] to [CatchRecordSummary]; `null` for an unrecognised/`Discarded` status. */
object DraftSummaryMappers {
    fun toDomain(row: DraftSummaryRow): CatchRecordSummary? {
        val status =
            DraftStatus.entries.firstOrNull { it.name == row.status }?.toRecordStatusTag() ?: return null
        return CatchRecordSummary(
            id = row.id,
            catchRecordReference = row.catchRecordReference,
            vesselId = row.vesselId,
            tripEndDate = toDate(row.returnDay, row.returnMonth, row.returnYear),
            status = status,
        )
    }

    private fun toDate(
        day: Int?,
        month: Int?,
        year: Int?,
    ): DmyDate? = if (day != null && month != null && year != null) DmyDate(day, month, year) else null
}
