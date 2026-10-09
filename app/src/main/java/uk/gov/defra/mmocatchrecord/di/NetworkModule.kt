package uk.gov.defra.mmocatchrecord.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import uk.gov.defra.mmocatchrecord.BuildConfig
import uk.gov.defra.mmocatchrecord.core.language.AppLanguageRepository
import uk.gov.defra.mmocatchrecord.core.network.AcceptLanguageInterceptor
import uk.gov.defra.mmocatchrecord.core.network.AuthInterceptor
import uk.gov.defra.mmocatchrecord.core.network.MissingTokenProvider
import uk.gov.defra.mmocatchrecord.core.network.StubTokenProvider
import uk.gov.defra.mmocatchrecord.core.network.TokenProvider
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Networking foundation (ADR 0014/0015): JSON, the per-flavor [TokenProvider] and the authenticated/
 * localised [OkHttpClient]. See [ReferenceDataRetrofitModule] for the Retrofit/`HealthApi` layer.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    private const val CONNECT_TIMEOUT_SECONDS = 15L
    private const val READ_TIMEOUT_SECONDS = 15L
    private const val WRITE_TIMEOUT_SECONDS = 15L

    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    /** Visible for testing both branches without compiling three build variants. */
    internal fun tokenProviderFor(useStubAuth: Boolean): TokenProvider =
        if (useStubAuth) StubTokenProvider() else MissingTokenProvider()

    @Provides
    @Singleton
    fun provideTokenProvider(): TokenProvider = tokenProviderFor(BuildConfig.USE_STUB_AUTH)

    @Provides
    @Singleton
    fun provideAuthInterceptor(tokenProvider: TokenProvider): AuthInterceptor = AuthInterceptor(tokenProvider)

    @Provides
    @Singleton
    fun provideAcceptLanguageInterceptor(appLanguageRepository: AppLanguageRepository): AcceptLanguageInterceptor =
        AcceptLanguageInterceptor(appLanguageRepository)

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        acceptLanguageInterceptor: AcceptLanguageInterceptor,
    ): OkHttpClient {
        val builder =
            OkHttpClient
                .Builder()
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .addInterceptor(acceptLanguageInterceptor)
                .addInterceptor(authInterceptor)
        if (BuildConfig.DEBUG) {
            // BASIC level (method/URL/response code/duration only) — never body-level, in case a
            // later phase's reference/catch data turns out to be sensitive. Token is redacted either way.
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                    redactHeader("Authorization")
                },
            )
        }
        return builder.build()
    }
}
