package uk.gov.defra.mmocatchrecord

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import uk.gov.defra.mmocatchrecord.core.logging.ReleaseTree
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

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Structured, redacted logging (ADR 0011): unrestricted Timber.DebugTree for local development,
        // a redacted, WARN+-only ReleaseTree in release builds — see ReleaseTree's doc comment.
        Timber.plant(if (BuildConfig.DEBUG) Timber.DebugTree() else ReleaseTree())
    }
}
