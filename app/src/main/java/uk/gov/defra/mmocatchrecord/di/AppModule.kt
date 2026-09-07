package uk.gov.defra.mmocatchrecord.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import uk.gov.defra.mmocatchrecord.core.security.BiometricPreferenceStore
import uk.gov.defra.mmocatchrecord.core.security.BiometricReentryPolicy
import uk.gov.defra.mmocatchrecord.core.security.BiometricRepository
import uk.gov.defra.mmocatchrecord.core.security.FakeBiometricRepository
import uk.gov.defra.mmocatchrecord.core.security.InMemoryBiometricPreferenceStore
import uk.gov.defra.mmocatchrecord.core.security.InMemorySessionStore
import uk.gov.defra.mmocatchrecord.core.security.SessionStore
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.FakeCatchRecordRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.CatchRecordRepository
import uk.gov.defra.mmocatchrecord.feature.home.data.FakeHomeRepository
import uk.gov.defra.mmocatchrecord.feature.home.domain.HomeRepository
import uk.gov.defra.mmocatchrecord.feature.map.data.FakeMapRepository
import uk.gov.defra.mmocatchrecord.feature.map.domain.MapRepository
import uk.gov.defra.mmocatchrecord.feature.signin.data.FakeSignInRepository
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInRepository
import java.util.UUID
import javax.inject.Singleton

/**
 * Stage-1 Hilt module. Binds Stage-1 fakes to their interfaces; real Keystore/DataStore/Room/Retrofit
 * -backed implementations replace these fakes in later stages without changing consumer code.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    fun provideClock(): () -> Long = { System.currentTimeMillis() }

    @Provides
    @Singleton
    fun provideCatchRecordIdFactory(): () -> String = { UUID.randomUUID().toString() }

    @Provides
    @Singleton
    fun provideBiometricReentryPolicy(): BiometricReentryPolicy = BiometricReentryPolicy()

    @Provides
    @Singleton
    fun provideBiometricRepository(): BiometricRepository = FakeBiometricRepository()

    @Provides
    @Singleton
    fun provideSessionStore(): SessionStore = InMemorySessionStore()

    @Provides
    @Singleton
    fun provideBiometricPreferenceStore(): BiometricPreferenceStore = InMemoryBiometricPreferenceStore()

    // feature.signin
    @Provides
    @Singleton
    fun provideSignInRepository(): SignInRepository = FakeSignInRepository()

    // feature.home
    @Provides
    @Singleton
    fun provideHomeRepository(): HomeRepository = FakeHomeRepository()

    // feature.catchrecord
    @Provides
    @Singleton
    fun provideCatchRecordRepository(): CatchRecordRepository = FakeCatchRecordRepository()

    // feature.map
    @Provides
    @Singleton
    fun provideMapRepository(): MapRepository = FakeMapRepository()
}
