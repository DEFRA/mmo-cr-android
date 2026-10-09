package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val PERIODIC_REFRESH_HOURS = 12L
private const val BACKOFF_DELAY_MINUTES = 15L
private const val UNIQUE_PERIODIC_WORK_NAME = "reference-data-periodic-refresh"
private const val UNIQUE_ONE_TIME_WORK_NAME = "reference-data-startup-refresh"

/**
 * Schedules [ReferenceDataSyncWorker]: a `CONNECTED`-constrained unique periodic refresh every
 * [PERIODIC_REFRESH_HOURS] hours (`KEEP`, so re-calling this never resets an already-pending period — see
 * ADR 0015 for the period rationale) plus a one-time refresh at app start (`REPLACE`, so a second app start
 * before the first finishes supersedes it rather than queuing a duplicate).
 */
class WorkManagerReferenceDataSyncScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun schedulePeriodicRefresh() {
            val request =
                PeriodicWorkRequestBuilder<ReferenceDataSyncWorker>(PERIODIC_REFRESH_HOURS, TimeUnit.HOURS)
                    .setConstraints(connectedConstraints())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY_MINUTES, TimeUnit.MINUTES)
                    .build()
            WorkManager
                .getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun scheduleStartupRefresh() {
            val request =
                OneTimeWorkRequestBuilder<ReferenceDataSyncWorker>()
                    .setConstraints(connectedConstraints())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY_MINUTES, TimeUnit.MINUTES)
                    .build()
            WorkManager
                .getInstance(context)
                .enqueueUniqueWork(UNIQUE_ONE_TIME_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        private fun connectedConstraints(): Constraints =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    }
