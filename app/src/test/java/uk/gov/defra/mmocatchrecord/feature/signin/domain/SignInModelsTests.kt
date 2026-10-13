package uk.gov.defra.mmocatchrecord.feature.signin.domain

import org.junit.Assert.assertEquals
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
}
