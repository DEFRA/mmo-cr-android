package uk.gov.defra.mmocatchrecord.di

import kotlinx.coroutines.Dispatchers
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import uk.gov.defra.mmocatchrecord.core.root.SessionCoordinator
import uk.gov.defra.mmocatchrecord.core.security.BiometricPreferenceStore
import uk.gov.defra.mmocatchrecord.core.security.BiometricReentryPolicy
import uk.gov.defra.mmocatchrecord.core.security.BiometricRepository
import uk.gov.defra.mmocatchrecord.core.security.FakeBiometricRepository
import uk.gov.defra.mmocatchrecord.core.security.InMemoryBiometricPreferenceStore
import uk.gov.defra.mmocatchrecord.core.security.InMemorySessionStore
import uk.gov.defra.mmocatchrecord.core.security.SessionStore

/**
 * Stage-1 Koin module. Binds Stage-1 fakes to their interfaces; real Keystore/DataStore-backed
 * implementations replace these fakes in a later stage without changing consumer code.
 */
val appModule =
    module {
        single<BiometricRepository> { FakeBiometricRepository() }
        single<SessionStore> { InMemorySessionStore() }
        single<BiometricPreferenceStore> { InMemoryBiometricPreferenceStore() }
        single { BiometricReentryPolicy() }

        single { Dispatchers.Default }
        single<() -> Long> { { System.currentTimeMillis() } }

        viewModelOf(::SessionCoordinator)
    }
