package uk.gov.defra.mmocatchrecord.di

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.map.AssetMapDataRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraftRepository
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.ReferenceDataRepository
import uk.gov.defra.mmocatchrecord.feature.home.data.RoomHomeRepository

/**
 * [RepositoryModule] is a plain Hilt `@Module object` — its `@Provides` functions are ordinary functions
 * that can be called directly without standing up a Hilt component, so each can be exercised here as a
 * simple "wires up the right implementation" unit test.
 */
@RunWith(RobolectricTestRunner::class)
class RepositoryModuleTests {
    @Test
    fun `provideMapDataRepository wires up the asset-backed implementation`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()

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
}
