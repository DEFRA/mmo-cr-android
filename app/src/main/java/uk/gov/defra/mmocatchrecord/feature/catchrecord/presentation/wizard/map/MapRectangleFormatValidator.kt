package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.map

/** Errors for the free-text "Other" statistical sub-rectangle entry (Phase 4, screen 3). */
enum class MapRectangleInputError {
    Required,
    Format,

    /** Correctly formatted but absent from [MapRectangleFormatValidator.validate]'s `knownCodes` — FR8. */
    NotFound,
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

    /**
     * [knownCodes] (already uppercased), when supplied, additionally rejects a correctly formatted code
     * that doesn't exist in it (FR8) — `null` skips this check, preserving the previous lenient
     * format-only behaviour for callers whose rectangle list is known-incomplete (e.g. the stub fallback
     * used while the comprehensive offline map dataset hasn't loaded) — see `MapScreenStatusContent`.
     */
    fun validate(
        rawValue: String,
        knownCodes: Set<String>? = null,
    ): MapRectangleValidationResult {
        val trimmedValue = rawValue.trim()
        return when {
            trimmedValue.isEmpty() ->
                MapRectangleValidationResult(error = MapRectangleInputError.Required)

            !FORMAT_REGEX.matches(trimmedValue) ->
                MapRectangleValidationResult(error = MapRectangleInputError.Format)

            knownCodes != null && trimmedValue.uppercase() !in knownCodes ->
                MapRectangleValidationResult(error = MapRectangleInputError.NotFound)

            else -> MapRectangleValidationResult(code = trimmedValue.uppercase())
        }
    }
}
