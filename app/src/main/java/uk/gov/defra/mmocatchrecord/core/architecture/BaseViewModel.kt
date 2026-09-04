package uk.gov.defra.mmocatchrecord.core.architecture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Base class for MVVM ViewModels following a unidirectional data flow (UDF):
 *
 * - UI observes [state], a single immutable [StateFlow] of type [S].
 * - UI sends intent via [dispatch], with events of type [E].
 * - Subclasses mutate state only through [updateState], never by exposing a mutable flow.
 *
 * @param initialState the state emitted before any event is processed.
 * @param defaultDispatcher the [CoroutineDispatcher] used for [launchInViewModelScope]; overridable so
 *   unit tests can inject a test dispatcher (e.g. `StandardTestDispatcher`) instead of relying on
 *   `Dispatchers.Main`.
 */
abstract class BaseViewModel<S : ViewState, E>(
    initialState: S,
    protected val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()

    /** Current state snapshot, useful for reducers that need the latest value synchronously. */
    protected val currentState: S
        get() = _state.value

    /**
     * Handle a UI-originated event. Subclasses implement the UDF reducer / use-case dispatch here.
     */
    abstract fun dispatch(event: E)

    /** Atomically update state via a pure reducer function. */
    protected fun updateState(reducer: (S) -> S) {
        _state.value = reducer(_state.value)
    }

    /** Launch coroutine work on [defaultDispatcher], scoped to this ViewModel's lifecycle. */
    protected fun launchInViewModelScope(block: suspend () -> Unit) {
        viewModelScope.launch(defaultDispatcher) {
            block()
        }
    }
}
