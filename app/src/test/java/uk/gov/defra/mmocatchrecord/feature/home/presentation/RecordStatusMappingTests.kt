package uk.gov.defra.mmocatchrecord.feature.home.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uk.gov.defra.mmocatchrecord.common.design.RecordStatusTag
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus

class RecordStatusMappingTests {
    @Test
    fun `every DraftStatus maps to its expected RecordStatusTag or null`() {
        assertEquals(RecordStatusTag.Draft, DraftStatus.Draft.toRecordStatusTag())
        assertEquals(RecordStatusTag.ReadyToSubmit, DraftStatus.ReadyToSubmit.toRecordStatusTag())
        assertEquals(RecordStatusTag.AwaitingSync, DraftStatus.PendingSync.toRecordStatusTag())
        assertEquals(RecordStatusTag.Submitted, DraftStatus.Submitted.toRecordStatusTag())
        assertNull(DraftStatus.Discarded.toRecordStatusTag())
    }

    @Test
    fun `every DraftStatus constant is covered by the mapping`() {
        DraftStatus.entries.forEach { status ->
            val tag = status.toRecordStatusTag()
            if (status == DraftStatus.Discarded) {
                assertNull(tag)
            } else {
                assertEquals(true, tag != null)
            }
        }
    }
}
