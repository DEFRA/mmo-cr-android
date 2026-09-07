package uk.gov.defra.mmocatchrecord

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Application entry point — root of the Hilt dependency graph (see [uk.gov.defra.mmocatchrecord.di.AppModule]). */
@HiltAndroidApp
class MmoApplication : Application()
