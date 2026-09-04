package uk.gov.defra.mmocatchrecord.core.architecture

/**
 * Marker contract for an immutable UI-state model exposed by a [BaseViewModel].
 *
 * Implementations should be immutable data classes so that every state emission is a distinct,
 * comparable value — this keeps Compose recomposition predictable and makes ViewModel unit tests
 * (asserting a sequence of emitted states) reliable.
 */
interface ViewState

/**
 * Generic status wrapper for asynchronous/loadable content within a [ViewState].
 *
 * Every screen must have an explicit state for each of these — in particular [Error] must always carry
 * enough information to render an accessible error message (text + icon + colour, never colour alone)
 * with a recovery action; [Loading] must never be left indefinitely with no route to [Content] or
 * [Error].
 */
sealed interface UiStatus<out T> {
    /** Nothing has been requested/loaded yet. */
    data object Idle : UiStatus<Nothing>

    /** A load/operation is in progress. */
    data object Loading : UiStatus<Nothing>

    /** Data loaded successfully. */
    data class Content<T>(
        val value: T,
    ) : UiStatus<T>

    /**
     * A recoverable or terminal failure occurred.
     *
     * @param message a plain-English, user-facing message (never a raw exception message or stack
     *   trace, and never containing PII/tokens/secrets).
     * @param isRetryable whether a retry action should be offered (true for transient/offline failures,
     *   false for terminal failures such as validation or auth errors).
     */
    data class Error(
        val message: String,
        val isRetryable: Boolean,
    ) : UiStatus<Nothing>
}
