package uk.gov.defra.mmocatchrecord.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import uk.gov.defra.mmocatchrecord.core.connectivity.AndroidConnectivityObserver
import uk.gov.defra.mmocatchrecord.core.connectivity.AndroidNetworkConnectivityChecker
import uk.gov.defra.mmocatchrecord.core.connectivity.ConnectivityObserver
import uk.gov.defra.mmocatchrecord.core.connectivity.NetworkConnectivityChecker
import uk.gov.defra.mmocatchrecord.core.language.AppLanguageRepository
import uk.gov.defra.mmocatchrecord.core.language.DataStoreAppLanguageRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local.CatchRecordDraftDao
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local.RoomCatchRecordDraftRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.map.AssetMapDataRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.StubReferenceDataRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.submission.StubCatchRecordSubmissionRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.sync.WorkManagerCatchRecordSyncScheduler
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraftRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordSubmissionRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordSyncScheduler
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.MapDataRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.ReferenceDataRepository
import uk.gov.defra.mmocatchrecord.feature.home.data.RoomHomeRepository
import uk.gov.defra.mmocatchrecord.feature.home.domain.HomeRepository
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
    fun provideHomeRepository(draftRepository: CatchRecordDraftRepository): HomeRepository =
        RoomHomeRepository(draftRepository)

    @Provides
    @Singleton
    fun provideCatchRecordDraftRepository(
        dao: CatchRecordDraftDao,
        clock: () -> Long,
    ): CatchRecordDraftRepository =
        RoomCatchRecordDraftRepository(
            dao = dao,
            clock = clock,
        )

    @Provides
    @Singleton
    fun provideReferenceDataRepository(): ReferenceDataRepository = StubReferenceDataRepository()

    @Provides
    @Singleton
    fun provideCatchRecordSubmissionRepository(): CatchRecordSubmissionRepository =
        StubCatchRecordSubmissionRepository()

    @Provides
    @Singleton
    fun provideNetworkConnectivityChecker(
        @ApplicationContext context: Context,
    ): NetworkConnectivityChecker = AndroidNetworkConnectivityChecker(context)

    @Provides
    @Singleton
    fun provideConnectivityObserver(
        @ApplicationContext context: Context,
    ): ConnectivityObserver = AndroidConnectivityObserver(context)

    @Provides
    @Singleton
    fun provideCatchRecordSyncScheduler(
        @ApplicationContext context: Context,
    ): CatchRecordSyncScheduler = WorkManagerCatchRecordSyncScheduler(context)

    @Provides
    @Singleton
    fun provideAppLanguageRepository(
        @ApplicationContext context: Context,
    ): AppLanguageRepository = DataStoreAppLanguageRepository(context)

    @Provides
    @Singleton
    fun provideMapDataRepository(
        @ApplicationContext context: Context,
    ): MapDataRepository = AssetMapDataRepository(context)
}
