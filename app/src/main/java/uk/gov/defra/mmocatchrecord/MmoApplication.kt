package uk.gov.defra.mmocatchrecord

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import uk.gov.defra.mmocatchrecord.core.config.AppConfiguration
import uk.gov.defra.mmocatchrecord.core.connectivity.ConnectivityObserver
import uk.gov.defra.mmocatchrecord.core.logging.ReleaseTree
import uk.gov.defra.mmocatchrecord.core.logging.StartupMetadataLogger
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.sync.WorkManagerReferenceDataSyncScheduler
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.sync.CatchRecordSyncSweep
import javax.inject.Inject

/**
 * Application entry point — root of the Hilt dependency graph (see [uk.gov.defra.mmocatchrecord.di.AppModule]).
 *
 * Implements [Configuration.Provider] so WorkManager's default on-demand initializer uses
 * [HiltWorkerFactory] to construct `@HiltWorker`-annotated workers (see
 * `feature.catchrecord.data.sync.CatchRecordSyncWorker`, ADR 0009) with their Hilt dependencies injected,
 * rather than requiring a no-arg constructor.
 */
@HiltAndroidApp
class MmoApplication :
    Application(),
    Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var referenceDataSyncScheduler: WorkManagerReferenceDataSyncScheduler

    @Inject
    lateinit var catchRecordSyncSweep: CatchRecordSyncSweep

    @Inject
    lateinit var appConfiguration: AppConfiguration

    // Injected eagerly so connectivity is tracked from process start, before any screen subscribes.
    @Inject
    lateinit var connectivityObserver: ConnectivityObserver

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Structured, redacted logging (ADR 0011): unrestricted Timber.DebugTree for local development,
        // a redacted, WARN+-only ReleaseTree in release builds — see ReleaseTree's doc comment.
        Timber.plant(if (BuildConfig.DEBUG) Timber.DebugTree() else ReleaseTree())
        StartupMetadataLogger.log(packageManager, packageName, BuildConfig.GIT_COMMIT_SHA, appConfiguration)
        // Manifest/ETag-driven reference-data cache refresh (ADR 0016): one now, one every ~12h after.
        referenceDataSyncScheduler.scheduleStartupRefresh()
        referenceDataSyncScheduler.schedulePeriodicRefresh()
        // Fire-and-forget reconciliation sweep (FR7, ADR 0014 Phase C) — must not block cold start.
        catchRecordSyncSweep.sweep()
    }
}
