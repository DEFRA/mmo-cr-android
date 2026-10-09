package uk.gov.defra.mmocatchrecord.di

import javax.inject.Qualifier

/** The reference-data service *root* URL/Retrofit (e.g. for [uk.gov.defra.mmocatchrecord.core.network.HealthApi]). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ReferenceDataServiceRoot

/** The reference-data `api/v1/reference-data/` base URL/Retrofit, for future dataset endpoints. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ReferenceDataApiBase
