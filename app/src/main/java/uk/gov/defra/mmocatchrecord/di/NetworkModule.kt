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
import uk.gov.defra.mmocatchrecord.core.security.SessionStore
import uk.gov.defra.mmocatchrecord.feature.signin.data.MsalTokenProvider
import uk.gov.defra.mmocatchrecord.feature.signin.data.NativeAuthClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Networking foundation (ADR 0015/0016): JSON, the per-flavor [TokenProvider] and the authenticated/
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

    /** Visible for testing all three flavors without compiling three build variants (ADR 0019 2.1d). */
    internal fun tokenProviderFor(
        useStubAuth: Boolean,
        useFakeSignIn: Boolean,
        referenceDataServiceUrl: String,
        nativeAuthClient: NativeAuthClient,
        sessionStore: SessionStore,
    ): TokenProvider =
        when {
            useStubAuth -> StubTokenProvider()
            // prod's empty URL (ADR 0016) is the fail-fast placeholder: never bind MSAL to it.
            !useFakeSignIn && referenceDataServiceUrl.isNotBlank() -> MsalTokenProvider(nativeAuthClient, sessionStore)
            else -> MissingTokenProvider()
        }

    @Provides
    @Singleton
    internal fun provideTokenProvider(
        nativeAuthClient: NativeAuthClient,
        sessionStore: SessionStore,
    ): TokenProvider =
        tokenProviderFor(
            useStubAuth = BuildConfig.USE_STUB_AUTH,
            useFakeSignIn = BuildConfig.USE_FAKE_SIGN_IN,
            referenceDataServiceUrl = BuildConfig.REFERENCE_DATA_SERVICE_URL,
            nativeAuthClient = nativeAuthClient,
            sessionStore = sessionStore,
        )

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
