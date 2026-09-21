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
 *
 * Uses [ExistingWorkPolicy.REPLACE] keyed by [draftId] so re-submitting/re-saving the same pending draft
 * (e.g. the user re-opens the app and the flow re-evaluates its state) only ever has one queued sync
 * request in flight for it, rather than piling up duplicates.
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
                .enqueueUniqueWork(uniqueWorkName(draftId), ExistingWorkPolicy.REPLACE, request)
        }

        companion object {
            fun uniqueWorkName(draftId: String): String = "catch-record-sync-$draftId"
        }
    }
