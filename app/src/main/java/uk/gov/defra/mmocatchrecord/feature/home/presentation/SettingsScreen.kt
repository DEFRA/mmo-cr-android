@file:Suppress("detekt.FunctionNaming", "detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.feature.home.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.common.design.GdsLinkAction
import uk.gov.defra.mmocatchrecord.common.design.MmoColors
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.common.design.RoyalCrestPlaceholder
import uk.gov.defra.mmocatchrecord.common.design.Spacing

/** Compose test tags for [SettingsTabContent] — see the "Your settings" design screenshot. */
object SettingsScreenTestTags {
    const val SCREEN = "settings_feature_screen"
    const val TITLE = "settings_feature_title"
    const val ANALYTICS_TOGGLE = "settings_feature_analytics_toggle"
    const val HOW_WE_USE_YOUR_DATA_LINK = "settings_feature_how_we_use_your_data_link"
    const val MY_ACCOUNT_LINK = "settings_feature_my_account_link"
    const val PRIVACY_NOTICE_LINK = "settings_feature_privacy_notice_link"
    const val SUPPORT_INFORMATION_LINK = "settings_feature_support_information_link"
    const val SIGN_OUT_LINK = "settings_feature_sign_out_link"
}

/**
 * "Your settings" screen — the content shown for the Settings tab of the app's bottom navigation (see
 * [HomeScreen]/[uk.gov.defra.mmocatchrecord.common.design.MmoBottomNavigationBar]).
 *
 * The optional-analytics toggle is **local UI state only** for now — no analytics/consent persistence
 * module exists yet in this app, so the preference is not currently saved or wired to any analytics SDK.
 * This is a known follow-up, not a silent omission.
 *
 * "My account", "Privacy notice" and "Support information" have no destination screens yet either — they
 * render as real, accessible, tappable links (not silently hidden) with a no-op action until those
 * screens are built; only "Sign out" is wired to a real action.
 */
@Suppress("FunctionNaming")
@Composable
fun SettingsTabContent(
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var analyticsEnabled by remember { mutableStateOf(false) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .testTag(SettingsScreenTestTags.SCREEN)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.testTag(SettingsScreenTestTags.TITLE).semantics { heading() },
        )

        AnalyticsConsentSection(
            enabled = analyticsEnabled,
            onEnabledChange = { analyticsEnabled = it },
        )

        HorizontalDivider(color = MmoColors.Grey3)

        SettingsLinkRow(
            number = 1,
            label = stringResource(R.string.settings_my_account),
            testTag = SettingsScreenTestTags.MY_ACCOUNT_LINK,
            onClick = {},
        )
        SettingsLinkRow(
            number = 2,
            label = stringResource(R.string.settings_privacy_notice),
            testTag = SettingsScreenTestTags.PRIVACY_NOTICE_LINK,
            onClick = {},
        )
        SettingsLinkRow(
            number = 3,
            label = stringResource(R.string.settings_support_information),
            testTag = SettingsScreenTestTags.SUPPORT_INFORMATION_LINK,
            onClick = {},
        )
        SettingsLinkRow(
            number = 4,
            label = stringResource(R.string.sign_out),
            testTag = SettingsScreenTestTags.SIGN_OUT_LINK,
            onClick = onSignOut,
        )

        Box(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.m), contentAlignment = Alignment.Center) {
            RoyalCrestPlaceholder()
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun AnalyticsConsentSection(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    val heading = stringResource(R.string.settings_analytics_heading)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = heading,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.settings_analytics_body),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                colors =
                    SwitchDefaults.colors(
                        checkedThumbColor = MmoColors.White,
                        checkedTrackColor = MmoColors.GovBlue,
                        uncheckedThumbColor = MmoColors.White,
                        uncheckedTrackColor = MmoColors.Grey2,
                        uncheckedBorderColor = MmoColors.Grey2,
                    ),
                modifier =
                    Modifier
                        .testTag(SettingsScreenTestTags.ANALYTICS_TOGGLE)
                        .semantics { contentDescription = heading },
            )
        }
        GdsLinkAction(
            text = stringResource(R.string.settings_how_we_use_your_data),
            onClick = {},
            testTag = SettingsScreenTestTags.HOW_WE_USE_YOUR_DATA_LINK,
        )
    }
}

@Suppress("FunctionNaming")
@Composable
private fun SettingsLinkRow(
    number: Int,
    label: String,
    testTag: String,
    onClick: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.minTouchTarget),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            // Purely a visual ordinal — the link text itself already fully describes the row to
            // assistive tech, so the number is not exposed separately (avoids "1, My account" clutter).
            Text(
                text = "$number)",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.clearAndSetSemantics {},
            )
            GdsLinkAction(
                text = label,
                onClick = onClick,
                testTag = testTag,
            )
        }
        HorizontalDivider(color = MmoColors.Grey3)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Suppress("FunctionNaming")
@Composable
fun SettingsTabContentPreview() {
    MmoTheme {
        Box(modifier = Modifier.background(MmoColors.White)) {
            SettingsTabContent(onSignOut = {})
        }
    }
}
