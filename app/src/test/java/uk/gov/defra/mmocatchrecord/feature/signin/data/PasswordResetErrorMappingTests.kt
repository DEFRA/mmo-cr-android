package uk.gov.defra.mmocatchrecord.feature.signin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** The pure SDK-result mapping (ADR 0027): every branch, in order, with no SDK type involved. */
class PasswordResetErrorMappingTests {
    private fun facts(
        browser: Boolean = false,
        network: Boolean = false,
        notFound: Boolean = false,
        invalidUsername: Boolean = false,
        invalidCode: Boolean = false,
        invalidPassword: Boolean = false,
        resetFailed: Boolean = false,
        serverError: String? = null,
        subError: String? = null,
    ) = ResetErrorFacts(
        isBrowserRequired = browser,
        hasNetworkCause = network,
        isUserNotFound = notFound,
        isInvalidUsername = invalidUsername,
        isInvalidCode = invalidCode,
        isInvalidPassword = invalidPassword,
        isPasswordResetFailed = resetFailed,
        serverError = serverError,
        subError = subError,
    )

    // region start

    @Test
    fun `start - browser required wins over everything`() {
        val all = facts(browser = true, network = true, notFound = true, serverError = "expired_token")

        assertEquals(NativeResetOutcome.BrowserRequired, all.toStartOutcome())
    }

    @Test
    fun `start - a network cause wins over account state`() {
        assertEquals(NativeResetOutcome.NetworkUnavailable, facts(network = true, notFound = true).toStartOutcome())
    }

    @Test
    fun `start - user not found and invalid username both look like a sent code with a dead-end token`() {
        val notFound = facts(notFound = true).toStartOutcome() as NativeResetOutcome.CodeRequired
        val invalid = facts(invalidUsername = true).toStartOutcome() as NativeResetOutcome.CodeRequired

        assertSame(DeadEndResetToken, notFound.token)
        assertSame(DeadEndResetToken, invalid.token)
    }

    @Test
    fun `start - throttling is recognised from the server strings`() {
        assertEquals(NativeResetOutcome.Throttled, facts(serverError = "too_many_requests").toStartOutcome())
        assertEquals(NativeResetOutcome.Throttled, facts(subError = "THROTTLED").toStartOutcome())
    }

    @Test
    fun `start - anything else is a safe unknown`() {
        assertEquals(NativeResetOutcome.Failed(RESET_START_ERROR), facts().toStartOutcome())
        assertEquals(NativeResetOutcome.Failed(RESET_START_ERROR), facts(serverError = "mystery").toStartOutcome())
    }

    @Test
    fun `start - every unmapped error collapses to one generic outcome so no account state leaks`() {
        val unmapped =
            listOf(
                facts(),
                facts(serverError = "user_not_found_extra"),
                facts(subError = "invalid_grant"),
                facts(serverError = "expired_token"),
                facts(invalidCode = true),
                facts(invalidPassword = true),
                facts(resetFailed = true),
            )

        val outcomes = unmapped.map { it.toStartOutcome() }.toSet()

        assertEquals(setOf<NativeResetOutcome>(NativeResetOutcome.Failed(RESET_START_ERROR)), outcomes)
    }

    // endregion

    // region submit code

    @Test
    fun `code - browser required then network take precedence`() {
        assertEquals(
            NativeResetOutcome.BrowserRequired,
            facts(browser = true, network = true, invalidCode = true).toSubmitCodeOutcome(),
        )
        assertEquals(
            NativeResetOutcome.NetworkUnavailable,
            facts(network = true, invalidCode = true).toSubmitCodeOutcome(),
        )
    }

    @Test
    fun `code - an expired server string wins over an invalid code flag`() {
        assertEquals(
            NativeResetOutcome.ExpiredSession,
            facts(invalidCode = true, subError = "expired_token").toSubmitCodeOutcome(),
        )
        assertEquals(NativeResetOutcome.ExpiredSession, facts(serverError = "Expired_Token").toSubmitCodeOutcome())
    }

    @Test
    fun `code - throttling wins over an invalid code flag`() {
        assertEquals(
            NativeResetOutcome.Throttled,
            facts(invalidCode = true, subError = "too_many_requests").toSubmitCodeOutcome(),
        )
    }

    @Test
    fun `code - the invalid code predicate or the invalid_oob_value string means an incorrect code`() {
        assertEquals(NativeResetOutcome.InvalidCode, facts(invalidCode = true).toSubmitCodeOutcome())
        assertEquals(NativeResetOutcome.InvalidCode, facts(subError = "invalid_oob_value").toSubmitCodeOutcome())
        assertEquals(NativeResetOutcome.InvalidCode, facts(serverError = "invalid_oob_value").toSubmitCodeOutcome())
    }

    @Test
    fun `code - anything else is a safe unknown`() {
        assertEquals(NativeResetOutcome.Failed(RESET_CODE_ERROR), facts().toSubmitCodeOutcome())
        assertEquals(NativeResetOutcome.Failed(RESET_CODE_ERROR), facts(subError = "mystery").toSubmitCodeOutcome())
    }

    // endregion

    // region submit password

    @Test
    fun `password - browser required then network take precedence`() {
        assertEquals(
            NativeResetOutcome.BrowserRequired,
            facts(browser = true, network = true, invalidPassword = true).toSubmitPasswordOutcome(),
        )
        assertEquals(
            NativeResetOutcome.NetworkUnavailable,
            facts(network = true, invalidPassword = true).toSubmitPasswordOutcome(),
        )
    }

    @Test
    fun `password - expired and throttled strings win over password flags`() {
        assertEquals(
            NativeResetOutcome.ExpiredSession,
            facts(invalidPassword = true, subError = "expired_token").toSubmitPasswordOutcome(),
        )
        assertEquals(
            NativeResetOutcome.Throttled,
            facts(resetFailed = true, serverError = "slow_down").toSubmitPasswordOutcome(),
        )
    }

    @Test
    fun `password - the invalid password predicate or a known sub-error means rejected`() {
        assertEquals(NativeResetOutcome.InvalidPassword, facts(invalidPassword = true).toSubmitPasswordOutcome())
        ResetServerStrings.invalidPassword.forEach {
            assertEquals(NativeResetOutcome.InvalidPassword, facts(subError = it).toSubmitPasswordOutcome())
        }
    }

    @Test
    fun `password - reset failed is a distinct safe failure`() {
        assertEquals(NativeResetOutcome.Failed(RESET_FAILED), facts(resetFailed = true).toSubmitPasswordOutcome())
    }

    @Test
    fun `password - anything else is a safe unknown`() {
        assertEquals(NativeResetOutcome.Failed(RESET_PASSWORD_ERROR), facts().toSubmitPasswordOutcome())
        assertEquals(
            NativeResetOutcome.Failed(RESET_PASSWORD_ERROR),
            facts(invalidUsername = true).toSubmitPasswordOutcome(),
        )
    }

    // endregion

    @Test
    fun `a thrown exception maps to network or a safe diagnostic`() {
        assertEquals(NativeResetOutcome.NetworkUnavailable, thrownResetOutcome(true, "diag"))
        assertEquals(NativeResetOutcome.Failed("diag"), thrownResetOutcome(false, "diag"))
    }

    @Test
    fun `hasNetworkCause walks the cause chain`() {
        assertTrue(hasNetworkCause(IOException("io")))
        assertTrue(hasNetworkCause(IllegalStateException("outer", RuntimeException("mid", IOException("root")))))
        assertFalse(hasNetworkCause(IllegalStateException("no io")))
        assertFalse(hasNetworkCause(null))
    }

    @Test
    fun `the dead-end token reveals nothing in toString`() {
        assertEquals("PasswordResetToken", DeadEndResetToken.toString())
    }

    @Test
    fun `an outcome never echoes the raw server strings`() {
        val outcome = facts(serverError = "secret-canary", subError = "secret-canary").toSubmitCodeOutcome()

        assertFalse(outcome.toString().contains("canary"))
    }
}
