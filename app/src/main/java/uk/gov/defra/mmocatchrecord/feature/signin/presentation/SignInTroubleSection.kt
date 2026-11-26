package uk.gov.defra.mmocatchrecord.feature.signin.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.common.design.ExpandableDetails
import uk.gov.defra.mmocatchrecord.common.design.MmoColors
import uk.gov.defra.mmocatchrecord.common.design.Spacing

/** Below-the-fold help on the sign-in screen: the password-recovery link and account-creation guidance. */
@Composable
internal fun SignInTroubleSection(
    onForgottenPassword: () -> Unit = {},
    forgottenPasswordEnabled: Boolean = true,
) {
    Text(
        text = stringResource(R.string.having_trouble_signing_in),
        style =
            MaterialTheme.typography.titleLarge.copy(
                color = MmoColors.Text,
                fontWeight = FontWeight.Bold,
            ),
        modifier = Modifier.padding(bottom = Spacing.s),
    )

    ForgottenPasswordLink(onClick = onForgottenPassword, enabled = forgottenPasswordEnabled)

    ExpandableDetails(
        title = stringResource(R.string.create_an_account),
        modifier = Modifier.padding(bottom = Spacing.xl),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(
                text = stringResource(R.string.vessel_owner_label),
                style =
                    MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MmoColors.Text,
                    ),
            )
            Text(
                text = stringResource(R.string.vessel_owner_text),
                style = MaterialTheme.typography.bodyMedium.copy(color = MmoColors.Text),
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = stringResource(R.string.skipper_or_agent_label),
                style =
                    MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MmoColors.Text,
                    ),
            )
            Text(
                text = stringResource(R.string.skipper_or_agent_text),
                style = MaterialTheme.typography.bodyMedium.copy(color = MmoColors.Text),
            )
        }
    }
}

@Composable
private fun ForgottenPasswordLink(
    onClick: () -> Unit,
    enabled: Boolean,
) {
    Text(
        text = stringResource(R.string.forgotten_your_password),
        style =
            MaterialTheme.typography.bodyLarge.copy(
                color = if (enabled) MmoColors.Link else MmoColors.SecondaryText,
                textDecoration = if (enabled) TextDecoration.Underline else TextDecoration.None,
            ),
        modifier =
            Modifier
                .padding(bottom = Spacing.l)
                .testTag(SignInScreenTestTags.FORGOTTEN_PASSWORD)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .sizeIn(minHeight = Spacing.minTouchTarget)
                .wrapContentSize(Alignment.CenterStart),
    )
}
