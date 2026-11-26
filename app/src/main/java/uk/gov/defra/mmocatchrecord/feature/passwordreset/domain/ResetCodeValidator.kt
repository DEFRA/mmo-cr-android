package uk.gov.defra.mmocatchrecord.feature.passwordreset.domain

import javax.inject.Inject

enum class ResetCodeError {
    Blank,
}

/** Blank-only check: the code length is never assumed because it would reveal account existence. */
class ResetCodeValidator
    @Inject
    constructor() {
        /** Strips every whitespace character, including those pasted in from an email. */
        fun normalise(raw: String): String = raw.filterNot { it.isWhitespace() }

        fun validate(raw: String): ResetCodeError? = if (normalise(raw).isEmpty()) ResetCodeError.Blank else null
    }
