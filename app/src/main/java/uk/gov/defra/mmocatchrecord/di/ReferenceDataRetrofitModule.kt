package uk.gov.defra.mmocatchrecord.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import uk.gov.defra.mmocatchrecord.BuildConfig
import uk.gov.defra.mmocatchrecord.core.network.HealthApi
import uk.gov.defra.mmocatchrecord.core.network.ReferenceDataServiceConfig
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.remote.ReferenceDataApi
import javax.inject.Singleton

/**
 * The reference-data service's two Retrofit instances (ADR 0016: `/health` at the service root, every
 * other route under `api/v1/reference-data/`) and the `HealthApi` built from the former.
 */
@Module
@InstallIn(SingletonComponent::class)
object ReferenceDataRetrofitModule {
    @Provides
    @Singleton
    @ReferenceDataServiceRoot
    fun provideReferenceDataServiceRootUrl(): String =
        ReferenceDataServiceConfig.requireServiceRootUrl(BuildConfig.REFERENCE_DATA_SERVICE_URL)

    @Provides
    @Singleton
    @ReferenceDataApiBase
    fun provideReferenceDataApiBaseUrl(
        @ReferenceDataServiceRoot serviceRootUrl: String,
    ): String = ReferenceDataServiceConfig.apiBaseUrl(serviceRootUrl)

    @Provides
    @Singleton
    @ReferenceDataServiceRoot
    fun provideReferenceDataServiceRootRetrofit(
        okHttpClient: OkHttpClient,
        json: Json,
        @ReferenceDataServiceRoot serviceRootUrl: String,
    ): Retrofit = buildRetrofit(okHttpClient, json, serviceRootUrl)

    @Provides
    @Singleton
    @ReferenceDataApiBase
    fun provideReferenceDataApiRetrofit(
        okHttpClient: OkHttpClient,
        json: Json,
        @ReferenceDataApiBase apiBaseUrl: String,
    ): Retrofit = buildRetrofit(okHttpClient, json, apiBaseUrl)

    @Provides
    @Singleton
    fun provideHealthApi(
        @ReferenceDataServiceRoot retrofit: Retrofit,
    ): HealthApi = retrofit.create(HealthApi::class.java)

    @Provides
    @Singleton
    internal fun provideReferenceDataApi(
        @ReferenceDataApiBase retrofit: Retrofit,
    ): ReferenceDataApi = retrofit.create(ReferenceDataApi::class.java)

    private fun buildRetrofit(
        okHttpClient: OkHttpClient,
        json: Json,
        baseUrl: String,
    ): Retrofit =
        Retrofit
            .Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
}
