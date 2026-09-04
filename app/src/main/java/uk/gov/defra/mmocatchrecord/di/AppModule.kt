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
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.FakeCatchRecordRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.CatchRecordRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.GetCatchRecordsUseCase
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.SaveCatchRecordUseCase
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.CatchRecordViewModel
import uk.gov.defra.mmocatchrecord.feature.home.data.FakeHomeRepository
import uk.gov.defra.mmocatchrecord.feature.home.domain.GetHomeSummaryUseCase
import uk.gov.defra.mmocatchrecord.feature.home.domain.HomeRepository
import uk.gov.defra.mmocatchrecord.feature.home.presentation.HomeViewModel
import uk.gov.defra.mmocatchrecord.feature.map.data.FakeMapRepository
import uk.gov.defra.mmocatchrecord.feature.map.domain.GetCatchLocationsUseCase
import uk.gov.defra.mmocatchrecord.feature.map.domain.MapRepository
import uk.gov.defra.mmocatchrecord.feature.map.presentation.MapViewModel
import uk.gov.defra.mmocatchrecord.feature.signin.data.FakeSignInRepository
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInRepository
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInUseCase
import uk.gov.defra.mmocatchrecord.feature.signin.presentation.SignInViewModel

/**
 * Stage-1 Koin module. Binds Stage-1 fakes to their interfaces; real Keystore/DataStore/Room/Retrofit
 * -backed implementations replace these fakes in later stages without changing consumer code.
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

        // feature.signin
        single<SignInRepository> { FakeSignInRepository() }
        single { SignInUseCase(get()) }
        viewModelOf(::SignInViewModel)

        // feature.home
        single<HomeRepository> { FakeHomeRepository() }
        single { GetHomeSummaryUseCase(get()) }
        viewModelOf(::HomeViewModel)

        // feature.catchrecord
        single<CatchRecordRepository> { FakeCatchRecordRepository() }
        single { SaveCatchRecordUseCase(get()) }
        single { GetCatchRecordsUseCase(get()) }
        viewModelOf(::CatchRecordViewModel)

        // feature.map
        single<MapRepository> { FakeMapRepository() }
        single { GetCatchLocationsUseCase(get()) }
        viewModelOf(::MapViewModel)
    }
