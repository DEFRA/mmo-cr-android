package uk.gov.defra.mmocatchrecord.debug

import androidx.compose.runtime.Composable
import uk.gov.defra.mmocatchrecord.common.design.DebugSettingsSection
import javax.inject.Inject

/** Release-variant no-op (CRAR-152 Phase C C5) — the failure-injection code does not exist in this build. */
class NoOpDebugSettingsSection
    @Inject
    constructor() : DebugSettingsSection {
        @Composable
        override fun Render() = Unit
    }
