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
import uk.gov.defra.mmocatchrecord.core.config.AppConfiguration
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.remote.CatchRecordApi
import javax.inject.Singleton

/** The catch-recording Retrofit, reusing the shared [OkHttpClient]; not wired in yet. See ADR 0026. */
@Module
@InstallIn(SingletonComponent::class)
object CatchRecordRetrofitModule {
    @Provides
    @Singleton
    @CatchRecordingServiceRoot
    fun provideCatchRecordingServiceRootUrl(appConfiguration: AppConfiguration): String =
        appConfiguration.requireCatchRecordingServiceRootUrl()

    @Provides
    @Singleton
    @CatchRecordJson
    fun provideCatchRecordJson(): Json = Json { ignoreUnknownKeys = true }

    @Provides
    @Singleton
    @CatchRecordingServiceRoot
    fun provideCatchRecordingRetrofit(
        okHttpClient: OkHttpClient,
        @CatchRecordJson json: Json,
        @CatchRecordingServiceRoot serviceRootUrl: String,
    ): Retrofit =
        Retrofit
            .Builder()
            .baseUrl(serviceRootUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    internal fun provideCatchRecordApi(
        @CatchRecordingServiceRoot retrofit: Retrofit,
    ): CatchRecordApi = retrofit.create(CatchRecordApi::class.java)
}
