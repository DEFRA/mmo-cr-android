package uk.gov.defra.mmocatchrecord.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.FakeCatchRecordRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.CatchRecordRepository
import uk.gov.defra.mmocatchrecord.feature.home.data.FakeHomeRepository
import uk.gov.defra.mmocatchrecord.feature.home.domain.HomeRepository
import uk.gov.defra.mmocatchrecord.feature.map.data.FakeMapRepository
import uk.gov.defra.mmocatchrecord.feature.map.domain.MapRepository
import uk.gov.defra.mmocatchrecord.feature.signin.data.FakeSignInRepository
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInRepository
import javax.inject.Singleton

/**
 * Hilt module for repository dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides
    @Singleton
    fun provideSignInRepository(): SignInRepository = FakeSignInRepository()

    @Provides
    @Singleton
    fun provideHomeRepository(): HomeRepository = FakeHomeRepository()

    @Provides
    @Singleton
    fun provideCatchRecordRepository(): CatchRecordRepository = FakeCatchRecordRepository()

    @Provides
    @Singleton
    fun provideMapRepository(): MapRepository = FakeMapRepository()
}
