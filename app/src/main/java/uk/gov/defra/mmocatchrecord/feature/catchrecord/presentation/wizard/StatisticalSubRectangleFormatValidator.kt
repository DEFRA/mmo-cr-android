package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard

/** Errors for the free-text "Other" statistical sub-rectangle entry (Phase 4, screen 3). */
enum class StatisticalSubRectangleInputError {
    Required,
    Format,
}

data class StatisticalSubRectangleValidationResult(
    val code: String? = null,
    val error: StatisticalSubRectangleInputError? = null,
) {
    val isValid: Boolean
        get() = code != null && error == null
}

/**
 * Pure validator for a manually-typed statistical sub-rectangle code, e.g. from the "Other" free-text
 * search (Phase 4, screen 3). The confirmed default format is 2 digits, 1 letter, 2 digits (e.g. "38E84"),
 * normalised to uppercase on success. Grid/radio-list selections (screens 1/2) are chosen from a known
 * list and so only need the simpler "is anything selected" required check, not this format rule.
 */
object StatisticalSubRectangleFormatValidator {
    private val FORMAT_REGEX = Regex("^\\d{2}[A-Za-z]\\d{2}$")

    fun validate(rawValue: String): StatisticalSubRectangleValidationResult {
        val trimmedValue = rawValue.trim()
        return when {
            trimmedValue.isEmpty() ->
                StatisticalSubRectangleValidationResult(error = StatisticalSubRectangleInputError.Required)

            !FORMAT_REGEX.matches(trimmedValue) ->
                StatisticalSubRectangleValidationResult(error = StatisticalSubRectangleInputError.Format)

            else -> StatisticalSubRectangleValidationResult(code = trimmedValue.uppercase())
        }
    }
}
