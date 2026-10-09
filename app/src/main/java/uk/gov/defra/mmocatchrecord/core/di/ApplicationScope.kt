package uk.gov.defra.mmocatchrecord.core.di

import javax.inject.Qualifier

/** Qualifies the process-lifetime `CoroutineScope` used for app-start reconciliation work (CRAR-152). */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope
