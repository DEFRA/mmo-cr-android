package uk.gov.defra.mmocatchrecord.di

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.gov.defra.mmocatchrecord.core.security.FakeBiometricRepository
import uk.gov.defra.mmocatchrecord.core.security.InMemoryBiometricPreferenceStore
import uk.gov.defra.mmocatchrecord.core.security.InMemorySessionStore

/** App providers are plain Hilt module methods, so direct calls verify the object wiring. */
class AppModuleTests {
    @Test
    fun `provideDispatcher wires up the default dispatcher`() {
        assertEquals(Dispatchers.Default, AppModule.provideDispatcher())
    }

    @Test
    fun `provideClock returns the current system time`() {
        val before = System.currentTimeMillis()
        val now = AppModule.provideClock()()
        val after = System.currentTimeMillis()

        assertTrue(now in before..after)
    }

    @Test
    fun `provideCatchRecordIdFactory generates distinct UUID strings`() {
        val idFactory = AppModule.provideCatchRecordIdFactory()

        assertNotNull(idFactory())
        assertTrue(idFactory() != idFactory())
    }

    @Test
    fun `provideBiometricReentryPolicy wires up a usable policy instance`() {
        assertNotNull(AppModule.provideBiometricReentryPolicy())
    }

    @Test
    fun `provideBiometricRepository wires up the Stage-1 fake`() {
        assertTrue(AppModule.provideBiometricRepository() is FakeBiometricRepository)
    }

    @Test
    fun `provideSessionStore wires up the in-memory implementation`() {
        assertTrue(AppModule.provideSessionStore() is InMemorySessionStore)
    }

    @Test
    fun `provideBiometricPreferenceStore wires up the in-memory implementation`() {
        assertTrue(AppModule.provideBiometricPreferenceStore() is InMemoryBiometricPreferenceStore)
    }

    @Test
    fun `provideApplicationScope returns an active coroutine scope`() {
        val scope = AppModule.provideApplicationScope()

        assertTrue(scope.isActive)
    }
}
