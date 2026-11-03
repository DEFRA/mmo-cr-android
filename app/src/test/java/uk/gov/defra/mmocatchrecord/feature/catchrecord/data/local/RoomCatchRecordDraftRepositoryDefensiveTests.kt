package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraftFactory

/**
 * Mock-backed companion to [RoomCatchRecordDraftRepositoryTests] (which exercises a real in-memory Room
 * database): covers the defensive branch that cannot be triggered through the real DAO — a newly created
 * draft that can't be immediately re-read back.
 */
class RoomCatchRecordDraftRepositoryDefensiveTests {
    @Test
    fun `startDraft fails defensively when the newly created draft cannot be re-read`() =
        runTest {
            val candidate =
                CatchRecordDraftFactory.newDraft(vesselId = "vessel-achilles", id = "draft-1", reference = "B14974-1")
            val createdEntity = DraftMappers.toEntities(candidate).draft
            val dao = mock<CatchRecordDraftDao>()
            whenever(dao.createDraft(any())).thenReturn(createdEntity)
            whenever(dao.getDraftWithChildren("draft-1")).thenReturn(null)
            val repository = RoomCatchRecordDraftRepository(dao = dao, clock = { 0L })

            val result = repository.startDraft(candidate)

            assertTrue(result.isFailure)
            assertTrue(
                result
                    .exceptionOrNull()
                    ?.message
                    .orEmpty()
                    .contains("vanished"),
            )
        }
}
