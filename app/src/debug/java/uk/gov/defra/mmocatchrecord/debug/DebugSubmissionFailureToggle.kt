package uk.gov.defra.mmocatchrecord.debug

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** In-memory debug-only toggle (CRAR-152 Phase C C5) — never persisted, so it cannot get stuck on. */
@Singleton
class DebugSubmissionFailureToggle
    @Inject
    constructor() {
        private val _enabled = MutableStateFlow(false)
        val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

        fun setEnabled(enabled: Boolean) {
            _enabled.value = enabled
        }
    }
