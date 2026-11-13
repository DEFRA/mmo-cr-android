package uk.gov.defra.mmocatchrecord.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import uk.gov.defra.mmocatchrecord.core.android.CurrentActivityProvider
import uk.gov.defra.mmocatchrecord.feature.signin.data.FakeSignInRepository
import uk.gov.defra.mmocatchrecord.feature.signin.data.MsalNativeAuthClient
import uk.gov.defra.mmocatchrecord.feature.signin.data.MsalSignInRepository
import uk.gov.defra.mmocatchrecord.feature.signin.data.NativeAuthClient
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInStage

/**
 * [SignInModule] is a plain Hilt `@Module object` (ADR 0019 Phase 2.1b) — its `@Provides` functions can
 * be called directly without standing up a Hilt component.
 */
@RunWith(RobolectricTestRunner::class)
class SignInModuleTests {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `provideNativeAuthClient wires up the MSAL-backed implementation`() {
        val client = SignInModule.provideNativeAuthClient(context, mock<CurrentActivityProvider>())

        assertTrue(client is MsalNativeAuthClient)
    }

    @Test
    fun `signInRepositoryFor binds the Stage-1 fake for local`() {
        val repository = SignInModule.signInRepositoryFor(useFakeSignIn = true, mock<NativeAuthClient>())

        assertTrue(repository is FakeSignInRepository)
    }

    @Test
    fun `signInRepositoryFor binds the MSAL-backed repository for dev and prod`() {
        val repository = SignInModule.signInRepositoryFor(useFakeSignIn = false, mock<NativeAuthClient>())

        assertTrue(repository is MsalSignInRepository)
    }

    @Test
    fun `provideSignInRepository delegates to signInRepositoryFor`() {
        val repository = SignInModule.provideSignInRepository(mock<NativeAuthClient>())

        assertNotNull(repository)
    }

    @Test
    fun `provideSignInStageObserver reuses the MSAL repository as its own stage observer`() {
        val repository = MsalSignInRepository(mock<NativeAuthClient>())

        val observer = SignInModule.provideSignInStageObserver(repository)

        assertTrue(observer === repository)
    }

    @Test
    fun `provideSignInStageObserver falls back to a no-op for a repository that isn't an observer`() {
        val observer = SignInModule.provideSignInStageObserver(FakeSignInRepository())

        assertTrue(observer.stage.value == SignInStage.Idle)
    }

    @Test
    fun `provideSignOutRepository reuses the MSAL repository as its own sign-out`() {
        val repository = MsalSignInRepository(mock<NativeAuthClient>())

        val signOutRepository = SignInModule.provideSignOutRepository(repository)

        assertTrue(signOutRepository === repository)
    }

    @Test
    fun `provideSignOutRepository falls back to a no-op for a repository that isn't one`() {
        val signOutRepository = SignInModule.provideSignOutRepository(FakeSignInRepository())

        runBlocking { signOutRepository.signOut() }
    }
}
