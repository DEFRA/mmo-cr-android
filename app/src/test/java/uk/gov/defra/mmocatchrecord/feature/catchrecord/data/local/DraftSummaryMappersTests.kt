package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uk.gov.defra.mmocatchrecord.common.design.RecordStatusTag
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DmyDate
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus

/** Pure mapper coverage for [DraftSummaryMappers] — every [DraftStatus] outcome and every partial-date case. */
class DraftSummaryMappersTests {
    private fun rowWith(
        status: String = DraftStatus.Draft.name,
        returnDay: Int? = 1,
        returnMonth: Int? = 1,
        returnYear: Int? = 2026,
    ) = DraftSummaryRow(
        id = "draft-1",
        vesselId = "vessel-1",
        catchRecordReference = "MMO-REF-001",
        status = status,
        returnDay = returnDay,
        returnMonth = returnMonth,
        returnYear = returnYear,
        createdAtEpochMillis = 0L,
        modifiedAtEpochMillis = 0L,
        submittedAtEpochMillis = null,
        syncedAtEpochMillis = null,
    )

    @Test
    fun `maps every non-discarded status to its record status tag`() {
        assertEquals(RecordStatusTag.Draft, DraftSummaryMappers.toDomain(rowWith(DraftStatus.Draft.name))?.status)
        assertEquals(
            RecordStatusTag.ReadyToSubmit,
            DraftSummaryMappers.toDomain(rowWith(DraftStatus.ReadyToSubmit.name))?.status,
        )
        assertEquals(
            RecordStatusTag.AwaitingSync,
            DraftSummaryMappers.toDomain(rowWith(DraftStatus.PendingSync.name))?.status,
        )
        assertEquals(
            RecordStatusTag.Submitted,
            DraftSummaryMappers.toDomain(rowWith(DraftStatus.Submitted.name))?.status,
        )
    }

    @Test
    fun `a discarded status maps to null so the row is filtered out`() {
        assertNull(DraftSummaryMappers.toDomain(rowWith(DraftStatus.Discarded.name)))
    }

    @Test
    fun `an unrecognised status string also maps to null`() {
        assertNull(DraftSummaryMappers.toDomain(rowWith("NotARealStatus")))
    }

    @Test
    fun `a fully populated return date maps to a DmyDate`() {
        val summary = DraftSummaryMappers.toDomain(rowWith(returnDay = 15, returnMonth = 11, returnYear = 2020))
        assertEquals(DmyDate(15, 11, 2020), summary?.tripEndDate)
    }

    @Test
    fun `a missing return day yields a null trip end date`() {
        val summary = DraftSummaryMappers.toDomain(rowWith(returnDay = null, returnMonth = 11, returnYear = 2020))
        assertNull(summary?.tripEndDate)
    }

    @Test
    fun `a missing return month yields a null trip end date`() {
        val summary = DraftSummaryMappers.toDomain(rowWith(returnDay = 15, returnMonth = null, returnYear = 2020))
        assertNull(summary?.tripEndDate)
    }

    @Test
    fun `a missing return year yields a null trip end date`() {
        val summary = DraftSummaryMappers.toDomain(rowWith(returnDay = 15, returnMonth = 11, returnYear = null))
        assertNull(summary?.tripEndDate)
    }

    @Test
    fun `all three date parts missing also yields a null trip end date`() {
        val summary = DraftSummaryMappers.toDomain(rowWith(returnDay = null, returnMonth = null, returnYear = null))
        assertNull(summary?.tripEndDate)
    }

    @Test
    fun `id, vesselId and catchRecordReference pass through unchanged`() {
        val row =
            rowWith().copy(id = "draft-9", vesselId = "vessel-9", catchRecordReference = "MMO-REF-009")
        val summary = DraftSummaryMappers.toDomain(row)
        assertEquals("draft-9", summary?.id)
        assertEquals("vessel-9", summary?.vesselId)
        assertEquals("MMO-REF-009", summary?.catchRecordReference)
    }
}
