package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow

import org.junit.Assert.assertNull
import org.junit.Test

/** Covers [DraftResumeRoute]'s `draftId` default — only exercised when a caller omits the argument. */
class CatchRecordRoutesTests {
    @Test
    fun `draft resume route defaults draftId to null when omitted`() {
        assertNull(DraftResumeRoute().draftId)
    }
}
