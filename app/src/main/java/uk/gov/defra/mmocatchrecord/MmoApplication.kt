package uk.gov.defra.mmocatchrecord

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
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
}
