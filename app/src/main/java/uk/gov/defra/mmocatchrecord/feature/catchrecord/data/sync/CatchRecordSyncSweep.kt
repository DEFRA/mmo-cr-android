package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.sync

import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import uk.gov.defra.mmocatchrecord.core.di.ApplicationScope
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local.CatchRecordDraftDao
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordSyncScheduler
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-start domain-state reconciliation (FR7, CRAR-152 Phase C) — re-enqueues drafts stranded at
 * `PendingSync` with no in-flight work; see ADR 0014. Repeat calls are safe: `KEEP` no-ops re-enqueues.
 */
@Singleton
class CatchRecordSyncSweep
    @Inject
    constructor(
        private val draftDao: Lazy<CatchRecordDraftDao>,
        private val scheduler: CatchRecordSyncScheduler,
        @ApplicationScope private val applicationScope: CoroutineScope,
    ) {
        /** [draftDao] is resolved lazily, off the calling thread, so a DB failure never crashes [sweep]'s caller. */
        fun sweep() {
            applicationScope.launch {
                runCatching { draftDao.get().findPendingSyncDraftIds() }
                    .onSuccess { ids -> ids.forEach { scheduleOne(it) } }
                    .onFailure { error -> Timber.w(error, "Reconciliation sweep could not read pending drafts") }
            }
        }

        private fun scheduleOne(draftId: String) {
            Timber.i("Reconciliation sweep re-enqueuing stranded PendingSync draft %s", draftId)
            scheduler.scheduleSync(draftId)
        }
    }
