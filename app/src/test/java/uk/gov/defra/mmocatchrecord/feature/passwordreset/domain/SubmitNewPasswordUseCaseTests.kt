package uk.gov.defra.mmocatchrecord.feature.passwordreset.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

class SubmitNewPasswordUseCaseTests {
    private val repository = RecordingPasswordResetRepository()
    private val useCase = SubmitNewPasswordUseCase(repository, PasswordPolicyValidator(PasswordPolicy()))
    private val token = TestResetToken()

    private fun chars(value: String) = value.toCharArray()

    private fun assertWiped(array: CharArray?) {
        assertTrue("expected a wiped array", array != null && array.all { it == '\u0000' })
    }

    @Test
    fun `a valid matching password is submitted and both arrays are wiped`() =
        runTest {
            val newPassword = chars("Abcdef1!")
            val confirmation = chars("Abcdef1!")

            val outcome = useCase(token, newPassword, confirmation)

            assertEquals(SubmitNewPasswordOutcome.Attempted(SubmitPasswordResult.Reset), outcome)
            assertEquals("Abcdef1!", repository.lastPasswordAtCall)
            assertWiped(newPassword)
            assertWiped(confirmation)
            assertWiped(repository.lastPasswordReference)
        }

    @Test
    fun `a blank new password is rejected without calling the repository`() =
        runTest {
            val outcome = useCase(token, chars(""), chars("Abcdef1!"))

            assertEquals(SubmitNewPasswordOutcome.Rejected(PasswordRuleFailure.Blank, null), outcome)
            assertNull(repository.lastPasswordReference)
        }

    @Test
    fun `a blank confirmation is rejected on the confirmation field only`() =
        runTest {
            val outcome = useCase(token, chars("Abcdef1!"), chars(""))

            assertEquals(SubmitNewPasswordOutcome.Rejected(null, ConfirmPasswordError.Blank), outcome)
            assertNull(repository.lastPasswordReference)
        }

    @Test
    fun `both fields blank are both reported`() =
        runTest {
            val outcome = useCase(token, chars(""), chars(""))

            assertEquals(
                SubmitNewPasswordOutcome.Rejected(PasswordRuleFailure.Blank, ConfirmPasswordError.Blank),
                outcome,
            )
        }

    @Test
    fun `mismatching passwords are rejected on the confirmation field and wiped`() =
        runTest {
            val newPassword = chars("Abcdef1!")
            val confirmation = chars("Abcdef1?")

            val outcome = useCase(token, newPassword, confirmation)

            assertEquals(SubmitNewPasswordOutcome.Rejected(null, ConfirmPasswordError.Mismatch), outcome)
            assertNull(repository.lastPasswordReference)
            assertWiped(newPassword)
            assertWiped(confirmation)
        }

    @Test
    fun `a policy failure is reported even when the confirmation matches`() =
        runTest {
            val outcome = useCase(token, chars("abcdefgh"), chars("abcdefgh"))

            assertEquals(SubmitNewPasswordOutcome.Rejected(PasswordRuleFailure.TooFewCategories, null), outcome)
        }

    @Test
    fun `a typed new password that fails policy and mismatches reports both`() =
        runTest {
            val outcome = useCase(token, chars("short"), chars("other"))

            assertEquals(
                SubmitNewPasswordOutcome.Rejected(PasswordRuleFailure.TooShort, ConfirmPasswordError.Mismatch),
                outcome,
            )
        }

    @Test
    fun `repository results are passed through`() =
        runTest {
            repository.submitPasswordResult = SubmitPasswordResult.PasswordRejected

            val outcome = useCase(token, chars("Abcdef1!"), chars("Abcdef1!"))

            assertEquals(SubmitNewPasswordOutcome.Attempted(SubmitPasswordResult.PasswordRejected), outcome)
        }

    @Test
    fun `a thrown exception is normalised to a safe failure and the password is still wiped`() =
        runTest {
            repository.thrown = IllegalStateException("raw-canary")
            val newPassword = chars("Abcdef1!")
            val confirmation = chars("Abcdef1!")

            val outcome = useCase(token, newPassword, confirmation) as SubmitNewPasswordOutcome.Attempted

            val failed = outcome.result as SubmitPasswordResult.Failed
            val unknown = failed.failure as PasswordResetFailure.Unknown
            assertTrue(!unknown.safeDiagnostic.contains("canary"))
            assertWiped(newPassword)
            assertWiped(confirmation)
        }

    @Test
    fun `cancellation is rethrown and the password is still wiped`() =
        runTest {
            repository.thrown = CancellationException("cancelled")
            val newPassword = chars("Abcdef1!")
            val confirmation = chars("Abcdef1!")

            val thrown = runCatching { useCase(token, newPassword, confirmation) }.exceptionOrNull()

            assertTrue(thrown is CancellationException)
            assertWiped(newPassword)
            assertWiped(confirmation)
        }
}
