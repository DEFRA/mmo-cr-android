package uk.gov.defra.mmocatchrecord.di

import android.os.SystemClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import uk.gov.defra.mmocatchrecord.core.config.AppConfiguration
import uk.gov.defra.mmocatchrecord.core.di.MonotonicClock
import uk.gov.defra.mmocatchrecord.core.di.PasswordResetStage
import uk.gov.defra.mmocatchrecord.core.di.ResetCodeLifetimeMinutes
import uk.gov.defra.mmocatchrecord.feature.passwordreset.data.FakePasswordResetRepository
import uk.gov.defra.mmocatchrecord.feature.passwordreset.data.MsalPasswordResetRepository
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.PasswordPolicy
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.PasswordResetRepository
import uk.gov.defra.mmocatchrecord.feature.signin.data.BrowserHandoff
import uk.gov.defra.mmocatchrecord.feature.signin.data.NativeAuthClient
import uk.gov.defra.mmocatchrecord.feature.signin.data.NativePasswordResetClient
import uk.gov.defra.mmocatchrecord.feature.signin.data.NoOpSignInStageObserver
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInStageObserver
import javax.inject.Singleton

/**
 * Hilt module for self-service password reset (ADR 0027). `USE_FAKE_SIGN_IN` picks the local-stage
 * [FakePasswordResetRepository] or the MSAL-backed [MsalPasswordResetRepository].
 */
@Module
@InstallIn(SingletonComponent::class)
object PasswordResetModule {
    @Provides
    fun providePasswordPolicy(): PasswordPolicy = PasswordPolicy()

    @Provides
    @Singleton
    internal fun provideBrowserHandoff(nativeAuthClient: NativeAuthClient): BrowserHandoff =
        BrowserHandoff(nativeAuthClient)

    /** Visible for testing both branches without compiling three build variants. */
    internal fun passwordResetRepositoryFor(
        useFakeSignIn: Boolean,
        client: NativePasswordResetClient,
        browserHandoff: BrowserHandoff,
    ): PasswordResetRepository =
        if (useFakeSignIn) FakePasswordResetRepository() else MsalPasswordResetRepository(client, browserHandoff)

    @Provides
    @Singleton
    internal fun providePasswordResetRepository(
        appConfiguration: AppConfiguration,
        client: NativePasswordResetClient,
        browserHandoff: BrowserHandoff,
    ): PasswordResetRepository =
        passwordResetRepositoryFor(appConfiguration.useFakeSignIn ?: false, client, browserHandoff)

    /** The MSAL repository reports the browser hand-off stage; the local fake never redirects. */
    @Provides
    @PasswordResetStage
    internal fun providePasswordResetStageObserver(repository: PasswordResetRepository): SignInStageObserver =
        repository as? SignInStageObserver ?: NoOpSignInStageObserver

    /** Elapsed-realtime clock: monotonic, so changing the device date cannot make a code look unexpired. */
    @Provides
    @MonotonicClock
    fun provideMonotonicClock(): () -> Long = SystemClock::elapsedRealtime

    @Provides
    @ResetCodeLifetimeMinutes
    fun provideResetCodeLifetimeMinutes(appConfiguration: AppConfiguration): Int =
        appConfiguration.passwordResetCodeLifetimeMinutes()
}
