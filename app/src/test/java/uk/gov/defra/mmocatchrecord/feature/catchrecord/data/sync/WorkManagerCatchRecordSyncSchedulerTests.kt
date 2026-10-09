package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.sync

import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.sync.WorkManagerCatchRecordSyncScheduler.Companion.uniqueWorkName

/**
 * Pins the [ExistingWorkPolicy.KEEP] duplicate-submission guard (ADR 0009 Phase C) and the unique-work
 * shape WorkManager actually enqueues, via `work-testing`'s synchronous executor.
 */
@RunWith(RobolectricTestRunner::class)
class WorkManagerCatchRecordSyncSchedulerTests {
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: WorkManagerCatchRecordSyncScheduler

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val config = Configuration.Builder().build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)
        scheduler = WorkManagerCatchRecordSyncScheduler(context)
    }

    @Test
    fun `scheduleSync enqueues unique work named catch-record-sync-id with KEEP`() {
        scheduler.scheduleSync("draft-1")

        val infos = workManager.getWorkInfosForUniqueWork(uniqueWorkName("draft-1")).get()
        assertEquals(1, infos.size)
        assertEquals(WorkInfo.State.ENQUEUED, infos.single().state)
    }

    @Test
    fun `scheduleSync requires a CONNECTED network constraint`() {
        scheduler.scheduleSync("draft-2")

        val info = workManager.getWorkInfosForUniqueWork(uniqueWorkName("draft-2")).get().single()
        assertEquals(androidx.work.NetworkType.CONNECTED, info.constraints.requiredNetworkType)
    }

    @Test
    fun `re-scheduling while already enqueued does not replace the existing request (KEEP)`() {
        scheduler.scheduleSync("draft-3")
        val firstId =
            workManager
                .getWorkInfosForUniqueWork(uniqueWorkName("draft-3"))
                .get()
                .single()
                .id

        scheduler.scheduleSync("draft-3")

        val infos = workManager.getWorkInfosForUniqueWork(uniqueWorkName("draft-3")).get()
        assertEquals(1, infos.size)
        assertEquals(firstId, infos.single().id)
        assertTrue(
            "KEEP must not cancel/replace an in-flight request",
            infos
                .single()
                .state.isFinished
                .not(),
        )
    }
}
