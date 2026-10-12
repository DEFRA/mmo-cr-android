package uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatchRecordDraftFactoryTests {
    @Test
    fun `newDraft sets the id, vessel and reference and leaves createdAt unstamped`() {
        val draft = CatchRecordDraftFactory.newDraft(vesselId = "vessel-achilles", id = "draft-1", reference = "ref-1")

        assertEquals("draft-1", draft.id)
        assertEquals("vessel-achilles", draft.vesselId)
        assertEquals("ref-1", draft.catchRecordReference)
        assertEquals(DraftStatus.Draft, draft.status)
        assertNull(draft.createdAtEpochMillis)
        assertNull(draft.submittedAtEpochMillis)
        assertNull(draft.syncedAtEpochMillis)
    }
}
