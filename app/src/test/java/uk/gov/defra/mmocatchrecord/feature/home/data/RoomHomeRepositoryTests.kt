package uk.gov.defra.mmocatchrecord.feature.home.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeCatchRecordDraftRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeReferenceDataRepository

/** Covers the vessel-id-to-display-name resolution fixed on the Home table (was showing raw ids). */
class RoomHomeRepositoryTests {
    @Test
    fun `resolves a known vesselId to its reference-data display name`() =
        runTest {
            val draftRepository = FakeCatchRecordDraftRepository()
            draftRepository.startDraft("vessel-achilles")
            val repository = RoomHomeRepository(draftRepository, FakeReferenceDataRepository())

            val summary = repository.observeSummary().first()

            assertEquals("ACHILLES", summary.catchRecords.single().vesselName)
            assertEquals("vessel-achilles", summary.catchRecords.single().vesselId)
        }

    @Test
    fun `falls back to the raw vesselId when it has no matching reference-data vessel`() =
        runTest {
            val draftRepository = FakeCatchRecordDraftRepository()
            draftRepository.startDraft("vessel-unknown")
            val repository = RoomHomeRepository(draftRepository, FakeReferenceDataRepository())

            val summary = repository.observeSummary().first()

            assertEquals("vessel-unknown", summary.catchRecords.single().vesselName)
        }
}
