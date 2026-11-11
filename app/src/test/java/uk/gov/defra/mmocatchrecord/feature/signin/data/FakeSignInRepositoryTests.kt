package uk.gov.defra.mmocatchrecord.feature.signin.data

import kotlinx.coroutines.test.runTest
import org.junit.Test

/** [FakeSignInRepository] has no MSAL account, so [FakeSignInRepository.signOut] is a no-op. */
class FakeSignInRepositoryTests {
    @Test
    fun `signOut completes without error`() =
        runTest {
            FakeSignInRepository().signOut()
        }
}
