package uk.gov.defra.mmocatchrecord.feature.passwordreset.domain

import javax.inject.Inject

enum class EmailAddressError {
    Blank,
    InvalidFormat,
}

/** Light client-side email check; the identity provider has the final say (AC2). */
class EmailAddressValidator
    @Inject
    constructor() {
        /** Returns the error for [raw], or `null` when it is acceptable. */
        fun validate(raw: String): EmailAddressError? {
            val email = raw.trim()
            return when {
                email.isEmpty() -> EmailAddressError.Blank
                isWellFormed(email) -> null
                else -> EmailAddressError.InvalidFormat
            }
        }

        private fun isWellFormed(email: String): Boolean {
            val at = email.indexOf('@')
            if (at <= 0 || at != email.lastIndexOf('@') || email.length > MAX_LENGTH) return false
            if (email.any { it.isWhitespace() }) return false
            val domain = email.substring(at + 1)
            val dot = domain.lastIndexOf('.')
            return dot > 0 && dot < domain.length - 1 && !domain.contains("..")
        }

        private companion object {
            const val MAX_LENGTH = 254
        }
    }
