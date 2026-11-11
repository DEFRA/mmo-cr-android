package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.sync

import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val UNIQUE_ONE_TIME_WORK_NAME = "reference-data-startup-refresh"
private const val UNIQUE_PERIODIC_WORK_NAME = "reference-data-periodic-refresh"

/**
 * Pins the unique-work shape and policies ADR 0016 relies on, via `work-testing`'s synchronous executor —
 * including [scheduleRefreshAfterSignIn] reusing [scheduleStartupRefresh]'s worker, name and `REPLACE` policy.
 */
@RunWith(RobolectricTestRunner::class)
class WorkManagerReferenceDataSyncSchedulerTests {
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: WorkManagerReferenceDataSyncScheduler

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
        workManager = WorkManager.getInstance(context)
        scheduler = WorkManagerReferenceDataSyncScheduler(context)
    }

    @Test
    fun `scheduleStartupRefresh enqueues unique work with a CONNECTED constraint`() {
        scheduler.scheduleStartupRefresh()

        val info = workManager.getWorkInfosForUniqueWork(UNIQUE_ONE_TIME_WORK_NAME).get().single()
        assertEquals(WorkInfo.State.ENQUEUED, info.state)
        assertEquals(NetworkType.CONNECTED, info.constraints.requiredNetworkType)
    }

    @Test
    fun `schedulePeriodicRefresh enqueues the periodic unique work`() {
        scheduler.schedulePeriodicRefresh()

        val info = workManager.getWorkInfosForUniqueWork(UNIQUE_PERIODIC_WORK_NAME).get().single()
        assertEquals(WorkInfo.State.ENQUEUED, info.state)
    }

    @Test
    fun `scheduleRefreshAfterSignIn reuses the startup work's unique name`() {
        scheduler.scheduleRefreshAfterSignIn()

        val infos = workManager.getWorkInfosForUniqueWork(UNIQUE_ONE_TIME_WORK_NAME).get()
        assertEquals(1, infos.size)
    }

    @Test
    fun `scheduleRefreshAfterSignIn replaces an in-flight startup refresh, not stacking a second one`() {
        scheduler.scheduleStartupRefresh()
        val startupId =
            workManager
                .getWorkInfosForUniqueWork(UNIQUE_ONE_TIME_WORK_NAME)
                .get()
                .single()
                .id

        scheduler.scheduleRefreshAfterSignIn()

        val infos = workManager.getWorkInfosForUniqueWork(UNIQUE_ONE_TIME_WORK_NAME).get()
        assertEquals(1, infos.size)
        // REPLACE superseded the pre-sign-in run with a fresh one that will read the now-available token.
        assertNotEquals(startupId, infos.single().id)
    }
}
