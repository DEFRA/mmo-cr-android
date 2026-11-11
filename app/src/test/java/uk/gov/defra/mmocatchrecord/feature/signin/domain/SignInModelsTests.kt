package uk.gov.defra.mmocatchrecord.feature.signin.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignInModelsTests {
    @Test
    fun `invalid credentials and network failures are distinct singleton instances`() {
        assertTrue(SignInFailure.InvalidCredentials != SignInFailure.Network)
    }

    @Test
    fun `unknown failure carries its message and supports equality by value`() {
        val first = SignInFailure.Unknown(message = "boom")
        val second = SignInFailure.Unknown(message = "boom")

        assertEquals(first, second)
        assertEquals("boom", first.message)
    }

    @Test
    fun `a sign in exception carries its failure and no message of its own`() {
        val exception = SignInException(SignInFailure.InvalidCredentials)

        assertEquals(SignInFailure.InvalidCredentials, exception.failure)
        // A null message keeps unsafe detail out of stack traces and crash reports.
        assertNull(exception.message)
    }

    @Test
    fun `a sign in session carries its user id and timestamp`() {
        val session = SignInSession(userId = "alice@example.com", signedInAtEpochMillis = 123L)

        assertEquals("alice@example.com", session.userId)
        assertEquals(123L, session.signedInAtEpochMillis)
    }
}
