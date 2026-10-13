package uk.gov.defra.mmocatchrecord.core.language

import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Covers [DataStoreAppLanguageRepository]'s default value and persisted-write round-trip (ADR 0012). */
@RunWith(RobolectricTestRunner::class)
class DataStoreAppLanguageRepositoryTests {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val repository = DataStoreAppLanguageRepository(context)

    @Test
    fun `language defaults to English then reflects a persisted setLanguage call`() =
        runTest {
            repository.language.test {
                assertEquals(AppLanguage.ENGLISH, awaitItem())

                repository.setLanguage(AppLanguage.WELSH)

                assertEquals(AppLanguage.WELSH, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
