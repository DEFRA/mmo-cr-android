package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local.CatchRecordDatabase
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local.CatchRecordDraftDao
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local.RoomCatchRecordDraftRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraftFactory
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.FakeCatchRecordSyncScheduler
import java.util.concurrent.Executor

/**
 * Plain unencrypted in-memory Room DB (same convention as `RoomCatchRecordDraftRepositoryTests`) so this
 * runs under Robolectric without SQLCipher; a same-thread executor keeps reads on the test dispatcher.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CatchRecordSyncSweepTests {
    private lateinit var database: CatchRecordDatabase
    private lateinit var dao: CatchRecordDraftDao
    private lateinit var repository: RoomCatchRecordDraftRepository
    private lateinit var scheduler: FakeCatchRecordSyncScheduler
    private var idCounter = 0

    @Before
    fun setUp() {
        val sameThreadExecutor = Executor { it.run() }
        database =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CatchRecordDatabase::class.java)
                .allowMainThreadQueries()
                .setQueryExecutor(sameThreadExecutor)
                .setTransactionExecutor(sameThreadExecutor)
                .build()
        dao = database.catchRecordDraftDao()
        repository = RoomCatchRecordDraftRepository(dao = dao, clock = { 1_000L })
        scheduler = FakeCatchRecordSyncScheduler()
    }

    private suspend fun seedDraft(
        vesselId: String,
        status: DraftStatus,
    ): String {
        val id = "draft-${idCounter++}"
        val started =
            repository
                .startDraft(CatchRecordDraftFactory.newDraft(vesselId = vesselId, id = id, reference = "ref-$id"))
                .getOrThrow()
        repository.saveDraft(started.copy(status = status)).getOrThrow()
        return id
    }

    private fun sweepWith(testScope: TestScope): CatchRecordSyncSweep {
        val applicationScope: CoroutineScope = testScope
        return CatchRecordSyncSweep(Lazy { dao }, scheduler, applicationScope)
    }

    @Test
    fun `only PendingSync drafts are swept, not Draft, ReadyToSubmit, Submitted or Discarded`() =
        runTest {
            seedDraft("vessel-draft", DraftStatus.Draft)
            seedDraft("vessel-ready", DraftStatus.ReadyToSubmit)
            val pendingId = seedDraft("vessel-pending", DraftStatus.PendingSync)
            seedDraft("vessel-submitted", DraftStatus.Submitted)
            seedDraft("vessel-discarded", DraftStatus.Discarded)

            sweepWith(this).sweep()
            advanceUntilIdle()

            assertEquals(listOf(pendingId), scheduler.scheduledDraftIds)
        }

    @Test
    fun `scheduleSync is called exactly once per stranded PendingSync draft`() =
        runTest {
            val first = seedDraft("vessel-1", DraftStatus.PendingSync)
            val second = seedDraft("vessel-2", DraftStatus.PendingSync)

            sweepWith(this).sweep()
            advanceUntilIdle()

            assertEquals(setOf(first, second), scheduler.scheduledDraftIds.toSet())
            assertEquals(2, scheduler.scheduledDraftIds.size)
        }

    @Test
    fun `an empty database sweeps zero drafts`() =
        runTest {
            sweepWith(this).sweep()
            advanceUntilIdle()

            assertTrue(scheduler.scheduledDraftIds.isEmpty())
        }

    @Test
    fun `repeated sweeps are safe and keep re-enqueuing the same stranded draft`() =
        runTest {
            val pendingId = seedDraft("vessel-1", DraftStatus.PendingSync)
            val sweep = sweepWith(this)

            sweep.sweep()
            advanceUntilIdle()
            sweep.sweep()
            advanceUntilIdle()

            assertEquals(listOf(pendingId, pendingId), scheduler.scheduledDraftIds)
        }
}
