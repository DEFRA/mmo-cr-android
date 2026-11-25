package uk.gov.defra.mmocatchrecord.feature.signin.data

import android.content.Context
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import uk.gov.defra.mmocatchrecord.core.android.CurrentActivityProvider

/**
 * The reset paths of [MsalNativeAuthClient] that never reach the SDK (dead-end token, foreign token);
 * the SDK-backed paths need a real tenant and are covered by the pure mapping tests and a gated device test.
 */
class MsalNativeAuthClientPasswordResetTests {
    private val client =
        MsalNativeAuthClient(mock<Context>(), { "{}" }, mock<CurrentActivityProvider>())

    @Test
    fun `any code against the dead-end token is an incorrect code`() =
        runTest {
            assertEquals(NativeResetOutcome.InvalidCode, client.submitResetCode(DeadEndResetToken, "12345678"))
            assertEquals(NativeResetOutcome.InvalidCode, client.submitResetCode(DeadEndResetToken, "00000000"))
        }

    @Test
    fun `a token from another step is a safe failure for a code`() =
        runTest {
            val outcome = client.submitResetCode(FakeResetToken("foreign"), "12345678")

            assertTrue(outcome is NativeResetOutcome.Failed)
        }

    @Test
    fun `a token that is not a password step is a safe failure and the password is still wiped`() =
        runTest {
            val password = ("Abc" + "def1!").toCharArray()

            val deadEnd = client.submitNewPassword(DeadEndResetToken, password)

            assertTrue(deadEnd is NativeResetOutcome.Failed)
            assertTrue(password.all { it == '\u0000' })

            val second = ("Abc" + "def1!").toCharArray()
            assertTrue(client.submitNewPassword(FakeResetToken("foreign"), second) is NativeResetOutcome.Failed)
            assertTrue(second.all { it == '\u0000' })
        }
}
