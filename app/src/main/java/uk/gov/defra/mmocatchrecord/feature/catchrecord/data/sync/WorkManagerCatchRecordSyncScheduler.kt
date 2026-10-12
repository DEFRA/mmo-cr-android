package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordSyncScheduler
import javax.inject.Inject

/**
 * WorkManager-backed [CatchRecordSyncScheduler] — see [CatchRecordSyncWorker] and ADR 0009.
 * [ExistingWorkPolicy.KEEP] avoids racing a duplicate submission against an already in-flight request.
 */
class WorkManagerCatchRecordSyncScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : CatchRecordSyncScheduler {
        override fun scheduleSync(draftId: String) {
            val constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            val request =
                OneTimeWorkRequestBuilder<CatchRecordSyncWorker>()
                    .setConstraints(constraints)
                    .setInputData(workDataOf(CatchRecordSyncWorker.KEY_DRAFT_ID to draftId))
                    .build()
            WorkManager
                .getInstance(context)
                .enqueueUniqueWork(uniqueWorkName(draftId), ExistingWorkPolicy.KEEP, request)
        }

        companion object {
            fun uniqueWorkName(draftId: String): String = "catch-record-sync-$draftId"
        }
    }
