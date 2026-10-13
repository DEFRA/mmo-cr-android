package uk.gov.defra.mmocatchrecord.di

import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local.CatchRecordDatabase
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local.CatchRecordDraftDao
import uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local.ReferenceDataDao

/**
 * [DatabaseModule] is a plain Hilt `@Module object` — its DAO accessor `@Provides` functions are ordinary
 * functions exercised directly here, the same pattern as [RepositoryModuleTests]. `provideCatchRecordDatabase`
 * itself calls [uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local.SqlCipherLibraryLoader] which
 * loads a native library unavailable on the JVM, so it is intentionally not exercised here.
 */
class DatabaseModuleTests {
    @Test
    fun `provideCatchRecordDraftDao delegates to the database's dao accessor`() {
        val dao = mock<CatchRecordDraftDao>()
        val database =
            mock<CatchRecordDatabase> {
                whenever(it.catchRecordDraftDao()).thenReturn(dao)
            }

        assertSame(dao, DatabaseModule.provideCatchRecordDraftDao(database))
    }

    @Test
    fun `provideReferenceDataDao delegates to the database's dao accessor`() {
        val dao = mock<ReferenceDataDao>()
        val database =
            mock<CatchRecordDatabase> {
                whenever(it.referenceDataDao()).thenReturn(dao)
            }

        assertSame(dao, DatabaseModule.provideReferenceDataDao(database))
    }
}
