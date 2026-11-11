package uk.gov.defra.mmocatchrecord.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import uk.gov.defra.mmocatchrecord.BuildConfig
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.feature.signin.data.FakeSignInRepository
import uk.gov.defra.mmocatchrecord.feature.signin.data.MsalNativeAuthClient
import uk.gov.defra.mmocatchrecord.feature.signin.data.MsalSignInRepository
import uk.gov.defra.mmocatchrecord.feature.signin.data.NativeAuthClient
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInRepository
import javax.inject.Singleton

/**
 * Hilt module for the sign-in feature (ADR 0019 Phase 2.1b). `local` keeps the Stage-1
 * [FakeSignInRepository]; `dev`/`prod` bind the real MSAL-backed [MsalSignInRepository].
 */
@Module
@InstallIn(SingletonComponent::class)
object SignInModule {
    @Provides
    @Singleton
    internal fun provideNativeAuthClient(
        @ApplicationContext context: Context,
    ): NativeAuthClient = MsalNativeAuthClient(context, R.raw.msal_native_auth_config)

    /** Visible for testing both branches without compiling three build variants. */
    internal fun signInRepositoryFor(
        useFakeSignIn: Boolean,
        nativeAuthClient: NativeAuthClient,
    ): SignInRepository = if (useFakeSignIn) FakeSignInRepository() else MsalSignInRepository(nativeAuthClient)

    @Provides
    @Singleton
    internal fun provideSignInRepository(nativeAuthClient: NativeAuthClient): SignInRepository =
        signInRepositoryFor(BuildConfig.USE_FAKE_SIGN_IN, nativeAuthClient)
}
