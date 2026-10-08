package uk.gov.defra.mmocatchrecord.common.design

import androidx.compose.runtime.Composable

/** Debug-only settings UI seam (CRAR-152 Phase C C5) — `release` binds a no-op, `debug` the real toggle. */
fun interface DebugSettingsSection {
    @Composable
    fun Render()
}
