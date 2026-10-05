package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.map

/** Errors for the free-text "Other" statistical sub-rectangle entry (Phase 4, screen 3). */
enum class MapRectangleInputError {
    Required,
    Format,
}

data class MapRectangleValidationResult(
    val code: String? = null,
    val error: MapRectangleInputError? = null,
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
object MapRectangleFormatValidator {
    private val FORMAT_REGEX = Regex("^\\d{2}[A-Za-z]\\d{2}$")

    fun validate(rawValue: String): MapRectangleValidationResult {
        val trimmedValue = rawValue.trim()
        return when {
            trimmedValue.isEmpty() ->
                MapRectangleValidationResult(error = MapRectangleInputError.Required)

            !FORMAT_REGEX.matches(trimmedValue) ->
                MapRectangleValidationResult(error = MapRectangleInputError.Format)

            else -> MapRectangleValidationResult(code = trimmedValue.uppercase())
        }
    }
}
