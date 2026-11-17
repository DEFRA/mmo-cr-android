package uk.gov.defra.mmocatchrecord.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import uk.gov.defra.mmocatchrecord.core.di.ApplicationScope
import uk.gov.defra.mmocatchrecord.core.security.BiometricPreferenceStore
import uk.gov.defra.mmocatchrecord.core.security.BiometricReentryPolicy
import uk.gov.defra.mmocatchrecord.core.security.BiometricRepository
import uk.gov.defra.mmocatchrecord.core.security.DataStoreSessionStore
import uk.gov.defra.mmocatchrecord.core.security.FakeBiometricRepository
import uk.gov.defra.mmocatchrecord.core.security.InMemoryBiometricPreferenceStore
import uk.gov.defra.mmocatchrecord.core.security.SessionStore
import uk.gov.defra.mmocatchrecord.core.security.createSessionDataStore
import java.util.UUID
import javax.inject.Singleton

/**
 * App-wide Hilt module. Session timing metadata is now persisted and encrypted (ADR 0019); the remaining
 * biometric bindings are still Stage-1 fakes replaced in a later stage without changing consumer code.
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
    fun provideSessionStore(
        @ApplicationContext context: Context,
    ): SessionStore = DataStoreSessionStore(createSessionDataStore(context))

    @Provides
    @Singleton
    fun provideBiometricPreferenceStore(): BiometricPreferenceStore = InMemoryBiometricPreferenceStore()

    /** Process-lifetime scope for app-start reconciliation work — see `CatchRecordSyncSweep`/ADR 0014. */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
