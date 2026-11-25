package uk.gov.defra.mmocatchrecord.feature.passwordreset.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.BrowserContinuationResult
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.StartResetResult
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.SubmitCodeResult
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.SubmitPasswordResult

class FakePasswordResetRepositoryTests {
    private val repository = FakePasswordResetRepository()

    @Test
    fun `every email appears to send a code so no account is disclosed`() =
        runTest {
            val known = repository.start("known@example.com")
            val unknown = repository.start("nobody@example.com")

            assertTrue(known is StartResetResult.CodeSent)
            assertTrue(unknown is StartResetResult.CodeSent)
        }

    @Test
    fun `the documented test codes behave predictably`() =
        runTest {
            val token = (repository.start("a@b.co") as StartResetResult.CodeSent).token

            assertTrue(repository.submitCode(token, "11111111") is SubmitCodeResult.Accepted)
            assertEquals(SubmitCodeResult.Expired, repository.submitCode(token, "00000000"))
            assertEquals(SubmitCodeResult.InvalidCode, repository.submitCode(token, "12345678"))
        }

    @Test
    fun `a password containing fail is rejected and any other resets, wiping the array`() =
        runTest {
            val token = (repository.start("a@b.co") as StartResetResult.CodeSent).token
            val rejected = "Abc1fail!".toCharArray()
            val accepted = "Abcdef1!".toCharArray()

            assertEquals(SubmitPasswordResult.PasswordRejected, repository.submitPassword(token, rejected))
            assertEquals(SubmitPasswordResult.Reset, repository.submitPassword(token, accepted))

            assertTrue(rejected.all { it == '\u0000' })
            assertTrue(accepted.all { it == '\u0000' })
        }

    @Test
    fun `the browser leg is cancelled and the token reveals nothing`() =
        runTest {
            val token = (repository.start("a@b.co") as StartResetResult.CodeSent).token

            assertEquals(BrowserContinuationResult.Cancelled, repository.continueInBrowser())
            assertEquals("PasswordResetToken", token.toString())
        }
}
