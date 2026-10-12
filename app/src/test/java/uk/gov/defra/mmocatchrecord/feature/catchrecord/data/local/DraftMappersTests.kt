package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus

class DraftMappersTests {
    private fun minimalDraft(): CatchRecordDraft =
        CatchRecordDraft(
            id = "draft-1",
            vesselId = "vessel-achilles",
            status = DraftStatus.Draft,
            modifiedAtEpochMillis = 1_000L,
            createdAtEpochMillis = 2_000L,
            submittedAtEpochMillis = 3_000L,
            syncedAtEpochMillis = 4_000L,
        )

    private fun toDomain(entity: DraftEntity): CatchRecordDraft =
        DraftMappers.toDomain(
            DraftWithChildren(
                draft = entity,
                gearUses = emptyList(),
                landingStorage = emptyList(),
                notLandedSpecies = emptyList(),
            ),
        )

    @Test
    fun `the three audit fields round-trip from domain to entity`() {
        val entity = DraftMappers.toEntities(minimalDraft()).draft

        assertEquals(2_000L, entity.createdAtEpochMillis)
        assertEquals(3_000L, entity.submittedAtEpochMillis)
        assertEquals(4_000L, entity.syncedAtEpochMillis)
    }

    @Test
    fun `the three audit fields round-trip from entity back to domain`() {
        val entity = DraftMappers.toEntities(minimalDraft()).draft

        val domain = toDomain(entity)

        assertEquals(2_000L, domain.createdAtEpochMillis)
        assertEquals(3_000L, domain.submittedAtEpochMillis)
        assertEquals(4_000L, domain.syncedAtEpochMillis)
    }

    @Test
    fun `null audit fields are preserved both directions`() {
        val draft =
            minimalDraft().copy(
                createdAtEpochMillis = null,
                submittedAtEpochMillis = null,
                syncedAtEpochMillis = null,
            )

        val entity = DraftMappers.toEntities(draft).draft
        assertNull(entity.createdAtEpochMillis)
        assertNull(entity.submittedAtEpochMillis)
        assertNull(entity.syncedAtEpochMillis)

        val domain = toDomain(entity)
        assertNull(domain.createdAtEpochMillis)
        assertNull(domain.submittedAtEpochMillis)
        assertNull(domain.syncedAtEpochMillis)
    }

    @Test
    fun `an unknown persisted status still throws DraftMappingException`() {
        val entity = DraftMappers.toEntities(minimalDraft()).draft.copy(status = "SomeRemovedStatus")

        assertThrows(DraftMappingException::class.java) { toDomain(entity) }
    }
}
