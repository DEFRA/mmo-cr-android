package uk.gov.defra.mmocatchrecord.feature.passwordreset.domain

import java.util.Arrays
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

enum class ConfirmPasswordError {
    Blank,
    Mismatch,
}

/** Outcome of [SubmitNewPasswordUseCase]; [Rejected] means the repository was never called. */
sealed interface SubmitNewPasswordOutcome {
    data class Rejected(
        val newPassword: PasswordRuleFailure?,
        val confirmation: ConfirmPasswordError?,
    ) : SubmitNewPasswordOutcome

    data class Attempted(
        val result: SubmitPasswordResult,
    ) : SubmitNewPasswordOutcome
}

/**
 * Checks the policy and that both entries match, then submits. Both arrays are wiped in a `finally`
 * whatever the result, so the caller must not reuse them.
 */
class SubmitNewPasswordUseCase
    @Inject
    constructor(
        private val repository: PasswordResetRepository,
        private val policyValidator: PasswordPolicyValidator,
    ) {
        @Suppress("TooGenericExceptionCaught")
        suspend operator fun invoke(
            token: PasswordResetToken,
            newPassword: CharArray,
            confirmation: CharArray,
        ): SubmitNewPasswordOutcome =
            try {
                val newPasswordFailure = policyValidator.validate(newPassword)
                val confirmationFailure = confirmationFailure(newPassword, confirmation)
                if (newPasswordFailure != null || confirmationFailure != null) {
                    SubmitNewPasswordOutcome.Rejected(newPasswordFailure, confirmationFailure)
                } else {
                    SubmitNewPasswordOutcome.Attempted(submit(token, newPassword))
                }
            } finally {
                Arrays.fill(newPassword, ZERO)
                Arrays.fill(confirmation, ZERO)
            }

        private suspend fun submit(
            token: PasswordResetToken,
            newPassword: CharArray,
        ): SubmitPasswordResult =
            try {
                repository.submitPassword(token, newPassword)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                SubmitPasswordResult.Failed(PasswordResetFailure.Unknown(UNEXPECTED_FAILURE))
            }

        private fun confirmationFailure(
            newPassword: CharArray,
            confirmation: CharArray,
        ): ConfirmPasswordError? =
            when {
                confirmation.isEmpty() -> ConfirmPasswordError.Blank
                // A blank new password already fails its own rule; only report a mismatch for a typed one.
                newPassword.isNotEmpty() && !newPassword.contentEquals(confirmation) -> ConfirmPasswordError.Mismatch
                else -> null
            }

        private companion object {
            const val ZERO = '\u0000'
            const val UNEXPECTED_FAILURE = "password reset submit threw an unexpected exception"
        }
    }
