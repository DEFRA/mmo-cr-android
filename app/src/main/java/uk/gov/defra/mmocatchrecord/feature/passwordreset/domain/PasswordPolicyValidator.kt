package uk.gov.defra.mmocatchrecord.feature.passwordreset.domain

import javax.inject.Inject

/** Entra's fixed rules: 8 to 256 characters with at least 3 of 4 character categories. */
data class PasswordPolicy(
    val minLength: Int = ENTRA_MIN_LENGTH,
    val maxLength: Int = ENTRA_MAX_LENGTH,
    val minCategories: Int = ENTRA_MIN_CATEGORIES,
) {
    companion object {
        const val ENTRA_MIN_LENGTH = 8
        const val ENTRA_MAX_LENGTH = 256
        const val ENTRA_MIN_CATEGORIES = 3
    }
}

enum class PasswordRuleFailure {
    Blank,
    TooShort,
    TooLong,
    TooFewCategories,
}

/**
 * Checks only what Entra checks (ADR 0027). Works on a [CharArray] so no immutable `String` copy of the
 * password is made; categories are lower, upper, digit and "other" (symbols, spaces, other scripts).
 */
class PasswordPolicyValidator
    @Inject
    constructor(
        private val policy: PasswordPolicy,
    ) {
        /** Returns the first failed rule for [password] (in the order of [PasswordRuleFailure]), or `null`. */
        fun validate(password: CharArray): PasswordRuleFailure? {
            if (password.isEmpty()) return PasswordRuleFailure.Blank
            val length = Character.codePointCount(password, 0, password.size)
            return when {
                length < policy.minLength -> PasswordRuleFailure.TooShort
                length > policy.maxLength -> PasswordRuleFailure.TooLong
                categoryCount(password) < policy.minCategories -> PasswordRuleFailure.TooFewCategories
                else -> null
            }
        }

        private fun categoryCount(password: CharArray): Int {
            var lower = false
            var upper = false
            var digit = false
            var other = false
            var index = 0
            while (index < password.size) {
                val codePoint = Character.codePointAt(password, index)
                when {
                    Character.isLowerCase(codePoint) -> lower = true
                    Character.isUpperCase(codePoint) -> upper = true
                    Character.isDigit(codePoint) -> digit = true
                    else -> other = true
                }
                index += Character.charCount(codePoint)
            }
            return listOf(lower, upper, digit, other).count { it }
        }
    }
