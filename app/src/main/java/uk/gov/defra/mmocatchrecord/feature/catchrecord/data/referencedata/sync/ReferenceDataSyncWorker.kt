package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import uk.gov.defra.mmocatchrecord.core.error.CatchRecordErrorClassification
import uk.gov.defra.mmocatchrecord.core.error.SafeErrorMapper

/**
 * Periodic + app-start background refresh of the cached reference data (ADR 0015) — see
 * [WorkManagerReferenceDataSyncScheduler] and [ReferenceDataRefresher]. Success/retry/failure mirror
 * `CatchRecordSyncWorker`'s reliance on WorkManager's own exponential backoff for [Result.retry].
 */
@HiltWorker
internal class ReferenceDataSyncWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val refresher: ReferenceDataRefresher,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result =
            runCatching { refresher.refresh() }
                .fold(
                    onSuccess = { result ->
                        when {
                            !result.hasAnyFailure -> Result.success()
                            result.hasAnyTransientFailure -> Result.retry()
                            else -> Result.failure()
                        }
                    },
                    onFailure = { error ->
                        Timber.w(error, "ReferenceDataSyncWorker: refresh failed outright")
                        if (SafeErrorMapper.classify(error) == CatchRecordErrorClassification.Transient) {
                            Result.retry()
                        } else {
                            Result.failure()
                        }
                    },
                )
    }
