package uk.gov.defra.mmocatchrecord.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import uk.gov.defra.mmocatchrecord.core.android.CurrentActivityProvider
import uk.gov.defra.mmocatchrecord.core.config.AppConfiguration
import uk.gov.defra.mmocatchrecord.feature.signin.data.FakeSignInRepository
import uk.gov.defra.mmocatchrecord.feature.signin.data.MsalNativeAuthClient
import uk.gov.defra.mmocatchrecord.feature.signin.data.MsalSignInRepository
import uk.gov.defra.mmocatchrecord.feature.signin.data.NativeAuthClient
import uk.gov.defra.mmocatchrecord.feature.signin.data.NoOpSignInStageObserver
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInRepository
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInStageObserver
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignOutRepository
import javax.inject.Singleton

/**
 * Hilt module for the sign-in feature (ADR 0019). The packaged config's `USE_FAKE_SIGN_IN` picks the Stage-1
 * [FakeSignInRepository] or the real MSAL-backed [MsalSignInRepository] (ADR 0024).
 */
@Module
@InstallIn(SingletonComponent::class)
object SignInModule {
    @Provides
    @Singleton
    internal fun provideNativeAuthClient(
        @ApplicationContext context: Context,
        appConfiguration: AppConfiguration,
        currentActivityProvider: CurrentActivityProvider,
    ): NativeAuthClient =
        MsalNativeAuthClient(context, appConfiguration::msalNativeAuthConfigJson, currentActivityProvider)

    /** Visible for testing both branches without compiling three build variants. */
    internal fun signInRepositoryFor(
        useFakeSignIn: Boolean,
        nativeAuthClient: NativeAuthClient,
    ): SignInRepository = if (useFakeSignIn) FakeSignInRepository() else MsalSignInRepository(nativeAuthClient)

    @Provides
    @Singleton
    internal fun provideSignInRepository(
        appConfiguration: AppConfiguration,
        nativeAuthClient: NativeAuthClient,
    ): SignInRepository = signInRepositoryFor(appConfiguration.useFakeSignIn ?: false, nativeAuthClient)

    /** Reuses the same [SignInRepository] singleton so its [SignInStageObserver.stage] is meaningful. */
    @Provides
    @Singleton
    internal fun provideSignInStageObserver(signInRepository: SignInRepository): SignInStageObserver =
        signInRepository as? SignInStageObserver ?: NoOpSignInStageObserver

    /** Falls back to a no-op for [FakeSignInRepository], which has no MSAL account to clear (ADR 0019 2.1d). */
    @Provides
    @Singleton
    internal fun provideSignOutRepository(signInRepository: SignInRepository): SignOutRepository =
        signInRepository as? SignOutRepository ?: SignOutRepository { true }
}
