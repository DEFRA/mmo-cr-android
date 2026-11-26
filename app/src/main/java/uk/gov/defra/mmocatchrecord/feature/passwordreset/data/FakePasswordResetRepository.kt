package uk.gov.defra.mmocatchrecord.feature.passwordreset.data

import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.BrowserContinuationResult
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.PasswordResetRepository
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.PasswordResetToken
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.StartResetResult
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.SubmitCodeResult
import uk.gov.defra.mmocatchrecord.feature.passwordreset.domain.SubmitPasswordResult
import java.util.Arrays

/**
 * Local-stage fake (`USE_FAKE_SIGN_IN`): code `11111111` valid, `00000000` expired, `fail` in a password rejected.
 */
internal class FakePasswordResetRepository : PasswordResetRepository {
    override suspend fun start(email: String): StartResetResult = StartResetResult.CodeSent(FakeResetToken)

    override suspend fun submitCode(
        token: PasswordResetToken,
        code: String,
    ): SubmitCodeResult =
        when (code) {
            VALID_CODE -> SubmitCodeResult.Accepted(FakeResetToken)
            EXPIRED_CODE -> SubmitCodeResult.Expired
            else -> SubmitCodeResult.InvalidCode
        }

    override suspend fun submitPassword(
        token: PasswordResetToken,
        password: CharArray,
    ): SubmitPasswordResult =
        try {
            if (password.containsMarker(REJECTED_PASSWORD_MARKER)) {
                SubmitPasswordResult.PasswordRejected
            } else {
                SubmitPasswordResult.Reset
            }
        } finally {
            Arrays.fill(password, '\u0000')
        }

    override suspend fun continueInBrowser(): BrowserContinuationResult = BrowserContinuationResult.Cancelled

    private fun CharArray.containsMarker(marker: String): Boolean =
        (0..size - marker.length).any { start -> marker.indices.all { this[start + it] == marker[it] } }

    private object FakeResetToken : PasswordResetToken {
        override fun toString(): String = "PasswordResetToken"
    }

    companion object {
        const val VALID_CODE = "11111111"
        const val EXPIRED_CODE = "00000000"
        const val REJECTED_PASSWORD_MARKER = "fail"
    }
}
