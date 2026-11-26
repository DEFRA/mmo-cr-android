package uk.gov.defra.mmocatchrecord.feature.passwordreset.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.BrowserContinuationResult
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.PasswordResetFailure
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.StartResetResult
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.SubmitCodeResult
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.SubmitPasswordResult
import uk.gov.defra.mmocatchrecord.feature.signin.data.BrowserHandoff
import uk.gov.defra.mmocatchrecord.feature.signin.data.FakeNativeAuthClient
import uk.gov.defra.mmocatchrecord.feature.signin.data.FakeResetToken
import uk.gov.defra.mmocatchrecord.feature.signin.data.NativeAuthOutcome
import uk.gov.defra.mmocatchrecord.feature.signin.data.NativeResetOutcome
import uk.gov.defra.mmocatchrecord.feature.signin.domain.SignInStage

/** [MsalPasswordResetRepository] against [FakeNativeAuthClient]: outcome-to-domain mapping per step. */
@OptIn(ExperimentalCoroutinesApi::class)
class MsalPasswordResetRepositoryTests {
    private val client = FakeNativeAuthClient()
    private val repository = MsalPasswordResetRepository(client, BrowserHandoff(client))
    private val token = FakeResetToken("incoming")

    // region start

    @Test
    fun `start maps CodeRequired to CodeSent carrying the same token`() =
        runTest {
            val next = FakeResetToken("next")
            client.startOutcome = NativeResetOutcome.CodeRequired(next)

            val result = repository.start("name@example.com")

            assertSame(next, (result as StartResetResult.CodeSent).token)
            assertEquals(listOf("name@example.com"), client.resetEmails)
        }

    @Test
    fun `start maps browser, network, throttled and failed outcomes`() =
        runTest {
            client.startOutcome = NativeResetOutcome.BrowserRequired
            assertEquals(StartResetResult.BrowserRequired, repository.start("a@b.co"))

            client.startOutcome = NativeResetOutcome.NetworkUnavailable
            assertEquals(StartResetResult.Failed(PasswordResetFailure.Network), repository.start("a@b.co"))

            client.startOutcome = NativeResetOutcome.Throttled
            assertEquals(StartResetResult.Failed(PasswordResetFailure.Throttled), repository.start("a@b.co"))

            client.startOutcome = NativeResetOutcome.Failed("diag")
            assertEquals(StartResetResult.Failed(PasswordResetFailure.Unknown("diag")), repository.start("a@b.co"))
        }

    @Test
    fun `start treats an outcome from another step as an unknown failure`() =
        runTest {
            client.startOutcome = NativeResetOutcome.Completed

            val result = repository.start("a@b.co") as StartResetResult.Failed

            assertTrue(result.failure is PasswordResetFailure.Unknown)
        }

    // endregion

    // region submit code

    @Test
    fun `submitCode maps each outcome`() =
        runTest {
            val next = FakeResetToken("next")
            client.submitCodeOutcome = NativeResetOutcome.PasswordRequired(next)
            assertSame(next, (repository.submitCode(token, "1") as SubmitCodeResult.Accepted).token)

            client.submitCodeOutcome = NativeResetOutcome.InvalidCode
            assertEquals(SubmitCodeResult.InvalidCode, repository.submitCode(token, "1"))

            client.submitCodeOutcome = NativeResetOutcome.ExpiredSession
            assertEquals(SubmitCodeResult.Expired, repository.submitCode(token, "1"))

            client.submitCodeOutcome = NativeResetOutcome.BrowserRequired
            assertEquals(SubmitCodeResult.BrowserRequired, repository.submitCode(token, "1"))

            client.submitCodeOutcome = NativeResetOutcome.NetworkUnavailable
            assertEquals(SubmitCodeResult.Failed(PasswordResetFailure.Network), repository.submitCode(token, "1"))

            client.submitCodeOutcome = NativeResetOutcome.Throttled
            assertEquals(SubmitCodeResult.Failed(PasswordResetFailure.Throttled), repository.submitCode(token, "1"))

            client.submitCodeOutcome = NativeResetOutcome.Failed("diag")
            assertEquals(
                SubmitCodeResult.Failed(PasswordResetFailure.Unknown("diag")),
                repository.submitCode(token, "1"),
            )
        }

    @Test
    fun `submitCode passes the token and code to the client`() =
        runTest {
            repository.submitCode(token, "12345678")

            assertSame(token, client.lastResetToken)
            assertEquals(listOf("12345678"), client.resetCodes)
        }

    @Test
    fun `submitCode treats an outcome from another step as an unknown failure`() =
        runTest {
            client.submitCodeOutcome = NativeResetOutcome.Completed

            val result = repository.submitCode(token, "1") as SubmitCodeResult.Failed

            assertTrue(result.failure is PasswordResetFailure.Unknown)
        }

    // endregion

    // region submit password

    @Test
    fun `submitPassword maps each outcome`() =
        runTest {
            client.submitPasswordOutcome = NativeResetOutcome.Completed
            assertEquals(SubmitPasswordResult.Reset, repository.submitPassword(token, chars()))

            client.submitPasswordOutcome = NativeResetOutcome.InvalidPassword
            assertEquals(SubmitPasswordResult.PasswordRejected, repository.submitPassword(token, chars()))

            client.submitPasswordOutcome = NativeResetOutcome.ExpiredSession
            assertEquals(SubmitPasswordResult.Expired, repository.submitPassword(token, chars()))

            client.submitPasswordOutcome = NativeResetOutcome.BrowserRequired
            assertEquals(SubmitPasswordResult.BrowserRequired, repository.submitPassword(token, chars()))

            client.submitPasswordOutcome = NativeResetOutcome.NetworkUnavailable
            assertEquals(
                SubmitPasswordResult.Failed(PasswordResetFailure.Network),
                repository.submitPassword(token, chars()),
            )

            client.submitPasswordOutcome = NativeResetOutcome.Throttled
            assertEquals(
                SubmitPasswordResult.Failed(PasswordResetFailure.Throttled),
                repository.submitPassword(token, chars()),
            )

            client.submitPasswordOutcome = NativeResetOutcome.Failed("diag")
            assertEquals(
                SubmitPasswordResult.Failed(PasswordResetFailure.Unknown("diag")),
                repository.submitPassword(token, chars()),
            )
        }

    @Test
    fun `submitPassword treats an outcome from another step as an unknown failure`() =
        runTest {
            client.submitPasswordOutcome = NativeResetOutcome.InvalidCode

            val result = repository.submitPassword(token, chars()) as SubmitPasswordResult.Failed

            assertTrue(result.failure is PasswordResetFailure.Unknown)
        }

    @Test
    fun `submitPassword wipes the password after the call, including when the client throws`() =
        runTest {
            val password = chars()
            repository.submitPassword(token, password)
            assertWiped(password)
            assertEquals("Abcdef1!", client.lastResetPasswordAtCall)

            client.resetThrown = IllegalStateException("boom")
            val second = chars()
            runCatching { repository.submitPassword(token, second) }
            assertWiped(second)
        }

    // endregion

    // region browser

    @Test
    fun `continueInBrowser maps each interactive outcome`() =
        runTest {
            client.interactiveOutcome = NativeAuthOutcome.Succeeded("user-1")
            val signedIn = repository.continueInBrowser() as BrowserContinuationResult.SignedIn
            assertEquals("user-1", signedIn.session.userId)

            client.interactiveOutcome = NativeAuthOutcome.BrowserCancelled
            assertEquals(BrowserContinuationResult.Cancelled, repository.continueInBrowser())

            client.interactiveOutcome = NativeAuthOutcome.NetworkUnavailable
            assertEquals(
                BrowserContinuationResult.Failed(PasswordResetFailure.Network),
                repository.continueInBrowser(),
            )

            client.interactiveOutcome = NativeAuthOutcome.Failed("diag")
            assertEquals(
                BrowserContinuationResult.Failed(PasswordResetFailure.Unknown("diag")),
                repository.continueInBrowser(),
            )
        }

    @Test
    fun `a second browser_required after the browser leg is terminal`() =
        runTest {
            client.interactiveOutcome = NativeAuthOutcome.BrowserRequired

            val result = repository.continueInBrowser() as BrowserContinuationResult.Failed

            assertTrue(result.failure is PasswordResetFailure.Unknown)
        }

    @Test
    fun `an unrelated browser-leg outcome is an unknown failure`() =
        runTest {
            client.interactiveOutcome = NativeAuthOutcome.CodeRequired

            val result = repository.continueInBrowser() as BrowserContinuationResult.Failed

            assertTrue(result.failure is PasswordResetFailure.Unknown)
        }

    @Test
    fun `the stage reports RedirectingToBrowser only while the browser leg is in flight`() =
        runTest {
            assertEquals(SignInStage.Idle, repository.stage.value)

            val job = launch { repository.continueInBrowser() }
            runCurrent()
            assertEquals(SignInStage.RedirectingToBrowser, repository.stage.value)

            repository.acknowledgeBrowserNoticeShown()
            job.join()
            assertEquals(SignInStage.Idle, repository.stage.value)
            assertEquals(1, client.interactiveCallCount)
        }

    // endregion

    private fun chars() = "Abcdef1!".toCharArray()

    private fun assertWiped(array: CharArray) = assertTrue(array.all { it == '\u0000' })
}
