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

/** The catch-recording service root URL/Retrofit (ADR 0026) — every route sits directly under it. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CatchRecordingServiceRoot

/** The submission-scoped [kotlinx.serialization.json.Json], kept separate from the shared instance so a
 *  future submission-only config change never touches Navigation typed-route serialization (ADR 0007/0022). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CatchRecordJson
