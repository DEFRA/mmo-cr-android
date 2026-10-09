package uk.gov.defra.mmocatchrecord.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.common.design.DebugSettingsSection
import uk.gov.defra.mmocatchrecord.common.design.Spacing
import javax.inject.Inject

/** Debug-build "force submission failures" settings row (CRAR-152 Phase C C5) — see ADR 0014. */
class DebugSettingsSectionImpl
    @Inject
    constructor(
        private val toggle: DebugSubmissionFailureToggle,
    ) : DebugSettingsSection {
        @Composable
        override fun Render() {
            val enabled by toggle.enabled.collectAsStateWithLifecycle()
            val label = stringResource(R.string.settings_debug_force_failures)
            Row(
                modifier = Modifier.fillMaxWidth().testTag("settings_debug_force_failures_row"),
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(
                    checked = enabled,
                    onCheckedChange = toggle::setEnabled,
                    modifier =
                        Modifier
                            .testTag("settings_debug_force_failures_toggle")
                            .semantics { contentDescription = label },
                )
            }
        }
    }
