package uk.gov.defra.mmocatchrecord.di

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.defra.mmocatchrecord.core.config.AppConfiguration
import uk.gov.defra.mmocatchrecord.feature.passwordreset.data.FakePasswordResetRepository
import uk.gov.defra.mmocatchrecord.feature.passwordreset.data.MsalPasswordResetRepository
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.PasswordPolicy
import uk.gov.defra.mmocatchrecord.feature.signin.data.BrowserHandoff
import uk.gov.defra.mmocatchrecord.feature.signin.data.NativeAuthClient
import uk.gov.defra.mmocatchrecord.feature.signin.data.NativePasswordResetClient
import uk.gov.defra.mmocatchrecord.feature.signin.data.NoOpSignInStageObserver

/** [PasswordResetModule] is a plain Hilt `@Module object`, so its `@Provides` functions are called directly. */
class PasswordResetModuleTests {
    private val resetClient = mock<NativePasswordResetClient>()
    private val handoff = BrowserHandoff(mock<NativeAuthClient>())

    @Test
    fun `passwordResetRepositoryFor binds the local fake when asked`() {
        val repository = PasswordResetModule.passwordResetRepositoryFor(true, resetClient, handoff)

        assertTrue(repository is FakePasswordResetRepository)
    }

    @Test
    fun `passwordResetRepositoryFor binds the MSAL-backed repository otherwise`() {
        val repository = PasswordResetModule.passwordResetRepositoryFor(false, resetClient, handoff)

        assertTrue(repository is MsalPasswordResetRepository)
    }

    @Test
    fun `providePasswordResetRepository follows the packaged config`() {
        val fake = mock<AppConfiguration>()
        whenever(fake.useFakeSignIn).thenReturn(true)

        val repository = PasswordResetModule.providePasswordResetRepository(fake, resetClient, handoff)

        assertTrue(repository is FakePasswordResetRepository)
    }

    @Test
    fun `providePasswordResetRepository defaults to the real repository when config is unavailable`() {
        val repository =
            PasswordResetModule.providePasswordResetRepository(mock<AppConfiguration>(), resetClient, handoff)

        assertTrue(repository is MsalPasswordResetRepository)
    }

    @Test
    fun `provideBrowserHandoff wraps the sign-in client`() {
        val first = PasswordResetModule.provideBrowserHandoff(mock<NativeAuthClient>())

        assertEquals(first.stage.value, handoff.stage.value)
    }

    @Test
    fun `stage observer is the repository when it reports stages`() {
        val repository = MsalPasswordResetRepository(resetClient, handoff)

        assertTrue(PasswordResetModule.providePasswordResetStageObserver(repository) === repository)
    }

    @Test
    fun `stage observer is a no-op for a repository that never redirects`() {
        val observer = PasswordResetModule.providePasswordResetStageObserver(FakePasswordResetRepository())

        assertTrue(observer === NoOpSignInStageObserver)
    }

    @Test
    fun `reset code lifetime comes from the packaged config`() {
        val config = mock<AppConfiguration>()
        whenever(config.passwordResetCodeLifetimeMinutes()).thenReturn(7)

        assertEquals(7, PasswordResetModule.provideResetCodeLifetimeMinutes(config))
    }

    @Test
    fun `monotonic clock is non-decreasing`() {
        val clock = PasswordResetModule.provideMonotonicClock()

        val first = clock()
        assertTrue(clock() >= first)
    }

    @Test
    fun `providePasswordPolicy carries Entra's documented limits`() {
        assertEquals(PasswordPolicy(), PasswordResetModule.providePasswordPolicy())
    }
}
