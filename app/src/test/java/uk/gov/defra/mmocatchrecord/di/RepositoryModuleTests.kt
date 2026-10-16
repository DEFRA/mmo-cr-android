package uk.gov.defra.mmocatchrecord.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.Json
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import uk.gov.defra.mmocatchrecord.core.connectivity.AndroidConnectivityObserver
import uk.gov.defra.mmocatchrecord.core.connectivity.AndroidNetworkConnectivityChecker
import uk.gov.defra.mmocatchrecord.core.connectivity.NetworkConnectivityChecker
import uk.gov.defra.mmocatchrecord.core.language.AppLanguageRepository
import uk.gov.defra.mmocatchrecord.core.language.DataStoreAppLanguageRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local.CatchRecordDraftDao
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local.ReferenceDataDao
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local.RoomCatchRecordDraftRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.map.AssetMapDataRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.CachingReferenceDataRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.remote.ReferenceDataApi
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.remote.ReferenceDataRemoteDataSource
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.referencedata.sync.WorkManagerReferenceDataSyncScheduler
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraftRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.ReferenceDataRepository
import uk.gov.defra.mmocatchrecord.feature.home.data.RoomHomeRepository
import uk.gov.defra.mmocatchrecord.feature.signin.data.FakeSignInRepository

/**
 * [RepositoryModule] is a plain Hilt `@Module object` — its `@Provides` functions are ordinary functions
 * that can be called directly without standing up a Hilt component, so each can be exercised here as a
 * simple "wires up the right implementation" unit test.
 */
@RunWith(RobolectricTestRunner::class)
class RepositoryModuleTests {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `provideMapDataRepository wires up the asset-backed implementation`() {
        val repository = RepositoryModule.provideMapDataRepository(context)

        assertNotNull(repository)
        assertTrue(repository is AssetMapDataRepository)
    }

    @Test
    fun `provideHomeRepository wires up the Room-backed implementation`() {
        val repository =
            RepositoryModule.provideHomeRepository(mock<CatchRecordDraftRepository>(), mock<ReferenceDataRepository>())

        assertNotNull(repository)
        assertTrue(repository is RoomHomeRepository)
    }

    @Test
    fun `provideSignInRepository wires up the Stage-1 fake`() {
        assertTrue(RepositoryModule.provideSignInRepository() is FakeSignInRepository)
    }

    @Test
    fun `provideCatchRecordDraftRepository wires up the Room-backed implementation`() {
        val repository =
            RepositoryModule.provideCatchRecordDraftRepository(mock<CatchRecordDraftDao>()) { 0L }

        assertTrue(repository is RoomCatchRecordDraftRepository)
    }

    @Test
    fun `provideReferenceDataRepository wires up the caching implementation`() {
        val remoteDataSource =
            ReferenceDataRemoteDataSource(mock<ReferenceDataApi>(), Json { ignoreUnknownKeys = true })
        val syncScheduler = WorkManagerReferenceDataSyncScheduler(context)

        val repository =
            RepositoryModule.provideReferenceDataRepository(
                dao = mock<ReferenceDataDao>(),
                remoteDataSource = remoteDataSource,
                syncScheduler = syncScheduler,
                connectivityChecker = mock<NetworkConnectivityChecker>(),
                appLanguageRepository = mock<AppLanguageRepository>(),
            )

        assertTrue(repository is CachingReferenceDataRepository)
    }

    @Test
    fun `provideNetworkConnectivityChecker wires up the Android-backed implementation`() {
        assertTrue(RepositoryModule.provideNetworkConnectivityChecker(context) is AndroidNetworkConnectivityChecker)
    }

    @Test
    fun `provideConnectivityObserver wires up the Android-backed implementation`() {
        val scope = CoroutineScope(Job())
        try {
            assertTrue(RepositoryModule.provideConnectivityObserver(context, scope) is AndroidConnectivityObserver)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `provideAppLanguageRepository wires up the DataStore-backed implementation`() {
        assertTrue(RepositoryModule.provideAppLanguageRepository(context) is DataStoreAppLanguageRepository)
    }
}
