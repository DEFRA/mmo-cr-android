package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.remote.ReferenceDataUnauthorizedException
import java.io.IOException

/**
 * Unit tests for [ReferenceDataSyncWorker]'s success/retry/failure mapping (ADR 0015), following the same
 * [TestListenableWorkerBuilder] + custom [WorkerFactory] pattern as `CatchRecordSyncWorkerTests` — a mocked
 * [ReferenceDataRefresher] stands in for Hilt's real `@AssistedInject` factory.
 */
@RunWith(RobolectricTestRunner::class)
class ReferenceDataSyncWorkerTests {
    @Test
    fun `every dataset succeeding returns success`() =
        runTest {
            val refresher = mock<ReferenceDataRefresher>()
            whenever(refresher.refresh())
                .thenReturn(ReferenceDataRefreshResult(mapOf("vessels" to Result.success(Unit))))

            val result = buildWorker(refresher).doWork()

            assertEquals(ListenableWorker.Result.success(), result)
        }

    @Test
    fun `a transient dataset failure requests a retry`() =
        runTest {
            val refresher = mock<ReferenceDataRefresher>()
            whenever(refresher.refresh())
                .thenReturn(ReferenceDataRefreshResult(mapOf("vessels" to Result.failure(IOException("offline")))))

            val result = buildWorker(refresher).doWork()

            assertEquals(ListenableWorker.Result.retry(), result)
        }

    @Test
    fun `a terminal-only dataset failure fails without retrying`() =
        runTest {
            val refresher = mock<ReferenceDataRefresher>()
            whenever(refresher.refresh()).thenReturn(
                ReferenceDataRefreshResult(
                    mapOf("vessels" to Result.failure(ReferenceDataUnauthorizedException("vessels"))),
                ),
            )

            val result = buildWorker(refresher).doWork()

            assertEquals(ListenableWorker.Result.failure(), result)
        }

    @Test
    fun `a mix of a transient and a terminal failure still requests a retry`() =
        runTest {
            val refresher = mock<ReferenceDataRefresher>()
            whenever(refresher.refresh()).thenReturn(
                ReferenceDataRefreshResult(
                    mapOf(
                        "vessels" to Result.failure(ReferenceDataUnauthorizedException("vessels")),
                        "ports" to Result.failure(IOException("offline")),
                    ),
                ),
            )

            val result = buildWorker(refresher).doWork()

            assertEquals(ListenableWorker.Result.retry(), result)
        }

    @Test
    fun `the manifest fetch itself failing transiently requests a retry`() =
        runTest {
            val refresher = mock<ReferenceDataRefresher>()
            whenever(refresher.refresh()).thenAnswer { throw IOException("offline") }

            val result = buildWorker(refresher).doWork()

            assertEquals(ListenableWorker.Result.retry(), result)
        }

    @Test
    fun `the manifest fetch itself failing with an unauthorized error fails without retrying`() =
        runTest {
            val refresher = mock<ReferenceDataRefresher>()
            whenever(refresher.refresh()).thenAnswer { throw ReferenceDataUnauthorizedException("manifest") }

            val result = buildWorker(refresher).doWork()

            assertEquals(ListenableWorker.Result.failure(), result)
        }

    private fun buildWorker(refresher: ReferenceDataRefresher): ReferenceDataSyncWorker {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val factory =
            object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker = ReferenceDataSyncWorker(appContext, workerParameters, refresher)
            }
        return TestListenableWorkerBuilder<ReferenceDataSyncWorker>(context).setWorkerFactory(factory).build()
    }
}
