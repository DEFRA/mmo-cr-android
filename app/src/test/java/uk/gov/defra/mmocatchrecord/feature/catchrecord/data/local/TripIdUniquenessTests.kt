package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraftFactory
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class TripIdUniquenessTests {
    private lateinit var database: CatchRecordDatabase
    private lateinit var dao: CatchRecordDraftDao
    private lateinit var repository: RoomCatchRecordDraftRepository
    private var idCounter = 0
    private var clockMillis = 1_700_000_000_000L

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    CatchRecordDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        dao = database.catchRecordDraftDao()
        repository = RoomCatchRecordDraftRepository(dao = dao, clock = { clockMillis })
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun start(vesselId: String): CatchRecordDraft {
        val reference = CatchRecordReferenceGenerator.generate("B14974", clockMillis)
        val candidate =
            CatchRecordDraftFactory.newDraft(
                vesselId = vesselId,
                id = "id-${idCounter++}",
                reference = reference,
            )
        return repository.startDraft(candidate).getOrThrow()
    }

    @Test
    fun `a Trip ID already in use in the same second is moved on to the next free second`() =
        runTest {
            val first = start("vessel-achilles")
            repository.saveDraft(first.copy(status = DraftStatus.Submitted)).getOrThrow()
            val second = start("vessel-achilles")
            val third = start("vessel-hercules")

            assertEquals("B14974-14112023-221320", first.catchRecordReference)
            assertEquals("B14974-14112023-221321", second.catchRecordReference)
            assertEquals("B14974-14112023-221322", third.catchRecordReference)
            val reloaded = repository.getDraftById(second.id).getOrThrow()
            assertEquals(second.catchRecordReference, reloaded?.catchRecordReference)
        }

    @Test
    fun `the repeated BST to GMT hour cannot produce a duplicate Trip ID`() =
        runTest {
            clockMillis = Instant.parse("2026-10-25T00:30:00Z").toEpochMilli()
            val bst = start("vessel-achilles")
            repository.saveDraft(bst.copy(status = DraftStatus.Submitted)).getOrThrow()
            clockMillis = Instant.parse("2026-10-25T01:30:00Z").toEpochMilli()
            val gmt = start("vessel-achilles")

            assertEquals("B14974-25102026-013000", bst.catchRecordReference)
            assertEquals("B14974-25102026-013001", gmt.catchRecordReference)
        }

    /** ADR 0018: two same-vessel drafts created in the same second both persist, one second apart. */
    @Test
    fun `two active drafts for the same vessel in the same second both persist with distinct Trip IDs`() =
        runTest {
            val first = start("vessel-achilles")
            val second = start("vessel-achilles")

            assertEquals("B14974-14112023-221320", first.catchRecordReference)
            assertEquals("B14974-14112023-221321", second.catchRecordReference)
            assertEquals(DraftStatus.Draft, repository.getDraftById(first.id).getOrThrow()?.status)
            assertEquals(DraftStatus.Draft, repository.getDraftById(second.id).getOrThrow()?.status)
        }

    @Test
    fun `the database itself rejects a duplicate Trip ID`() =
        runTest {
            val started = start("vessel-achilles")
            val clash =
                DraftMappers
                    .toEntities(
                        CatchRecordDraftFactory.newDraft(
                            vesselId = "vessel-hercules",
                            id = "clash",
                            reference = checkNotNull(started.catchRecordReference),
                        ),
                    ).draft

            assertEquals(-1L, dao.insertDraftIfAbsent(clash))
        }

    @Test
    fun `saving a draft with another record's Trip ID fails and leaves that record intact`() =
        runTest {
            val owner = start("vessel-achilles")
            repository.saveDraft(owner.copy(isTripToday = true)).getOrThrow()
            val other = start("vessel-hercules")

            val result = repository.saveDraft(other.copy(catchRecordReference = owner.catchRecordReference))

            assertTrue(result.isFailure)
            val survivor = repository.getDraftById(owner.id).getOrThrow()
            assertEquals(owner.catchRecordReference, survivor?.catchRecordReference)
            assertEquals(true, survivor?.isTripToday)
            val unchanged = repository.getDraftById(other.id).getOrThrow()
            assertEquals(other.catchRecordReference, unchanged?.catchRecordReference)
        }
}
