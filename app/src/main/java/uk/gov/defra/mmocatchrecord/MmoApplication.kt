package uk.gov.defra.mmocatchrecord

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import uk.gov.defra.mmocatchrecord.di.appModule

/** Application entry point — starts the Koin dependency graph. */
class MmoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@MmoApplication)
            modules(appModule)
        }
    }
}
