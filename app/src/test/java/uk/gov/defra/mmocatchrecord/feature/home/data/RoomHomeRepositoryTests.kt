package uk.gov.defra.mmocatchrecord.feature.home.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.ReferenceDataRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Vessel
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeCatchRecordDraftRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeReferenceDataRepository

/** Delegates every call to [delegate] except [getVessels], which always fails — drives the
 * `getOrDefault(emptyList())` fallback in [RoomHomeRepository.observeSummary]. */
private class FailingVesselsReferenceDataRepository(
    private val delegate: ReferenceDataRepository = FakeReferenceDataRepository(),
) : ReferenceDataRepository by delegate {
    override suspend fun getVessels(): Result<List<Vessel>> =
        Result.failure(java.io.IOException("Simulated reference-data failure"))
}

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

    @Test
    fun `resolves a mix of known and unknown vessels within the same emitted summary`() =
        runTest {
            // Distinct ids: the default idFactory is constant, which would collapse both drafts into one.
            var nextId = 0
            val draftRepository = FakeCatchRecordDraftRepository(idFactory = { "draft-${nextId++}" })
            draftRepository.startDraft("vessel-achilles")
            draftRepository.startDraft("vessel-unresolved")
            val repository = RoomHomeRepository(draftRepository, FakeReferenceDataRepository())

            val summary = repository.observeSummary().first()

            val namesByVesselId = summary.catchRecords.associate { it.vesselId to it.vesselName }
            assertEquals("ACHILLES", namesByVesselId["vessel-achilles"])
            assertEquals("vessel-unresolved", namesByVesselId["vessel-unresolved"])
        }

    @Test
    fun `falls back to an empty vessel name lookup when reference data fails to load`() =
        runTest {
            val draftRepository = FakeCatchRecordDraftRepository()
            draftRepository.startDraft("vessel-achilles")
            val repository = RoomHomeRepository(draftRepository, FailingVesselsReferenceDataRepository())

            val summary = repository.observeSummary().first()

            // No vessel names resolve, so every record falls back to its raw vesselId — proves the
            // `getVessels().getOrDefault(emptyList())` fallback, not a crash propagating from the failure.
            assertEquals("vessel-achilles", summary.catchRecords.single().vesselName)
        }
}
