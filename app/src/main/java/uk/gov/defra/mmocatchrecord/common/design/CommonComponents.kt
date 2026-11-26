@file:Suppress("detekt.FunctionNaming", "detekt.MagicNumber")

package uk.gov.defra.mmocatchrecord.common.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontWeight.Companion.Normal
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.gov.defra.mmocatchrecord.R

/** GOV.UK Important Notification Banner */
@Suppress("FunctionNaming")
@Composable
fun ImportantNotificationBanner(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        // Banner header
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MmoColors.GovBlue)
                    .padding(horizontal = Spacing.s, vertical = Spacing.xs),
        ) {
            Text(
                text = title,
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        color = MmoColors.White,
                        fontWeight = FontWeight.Bold,
                    ),
            )
        }
        // Banner content box: white with a blue border on all sides, full width
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .border(width = Spacing.xxs, color = MmoColors.GovBlue)
                    .background(MmoColors.White)
                    .padding(horizontal = Spacing.s, vertical = Spacing.s),
        ) {
            Text(
                text = message,
                style =
                    MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MmoColors.Text,
                        lineHeight = 22.sp,
                    ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Persistent "Offline" banner shown on every catch-record wizard screen (see
 * [uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordWizardScaffold])
 * while the device has no connectivity — see the confirmed screenshot. State is never conveyed by colour
 * alone: the red tag carries the word "Offline" as well as its colour, alongside a plain-English message.
 * The two are merged into a single semantics node with a polite live region so TalkBack announces the
 * change once, without the assertive interruption used for genuine errors (see `WizardErrorState`) — going
 * offline is expected/recoverable, not a failure the user needs to act on immediately.
 */
@Suppress("FunctionNaming")
@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.offline_banner_label)
    val message = stringResource(R.string.offline_banner_message)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .testTag("offline_banner")
                .semantics(mergeDescendants = true) {
                    liveRegion = LiveRegionMode.Polite
                },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                color = MmoColors.ErrorRed,
                shape = RoundedCornerShape(2.dp),
            ) {
                Text(
                    text = label,
                    style =
                        MaterialTheme.typography.bodyLarge.copy(
                            color = MmoColors.White,
                            fontWeight = FontWeight.Bold,
                        ),
                    modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs),
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge.copy(color = MmoColors.Text),
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalDivider(color = MmoColors.Grey2)
    }
}

/** Reusable GOV.UK green primary action button */
@Suppress("FunctionNaming")
@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    GdsButtonFrame(
        onClick = onClick,
        enabled = enabled,
        colors =
            if (enabled) {
                GdsButtonColors(
                    fill = MmoColors.SuccessGreen,
                    pressedFill = PRIMARY_BUTTON_SHADOW,
                    content = MmoColors.White,
                    edge = PRIMARY_BUTTON_SHADOW,
                )
            } else {
                GdsButtonColors(fill = MmoColors.Grey2, pressedFill = MmoColors.Grey2, content = MmoColors.Grey1)
            },
        modifier = modifier.fillMaxWidth(),
    ) { contentColor ->
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = Normal, color = contentColor),
        )
    }
}

private val PRIMARY_BUTTON_SHADOW = Color(0xFF005A30)

/** Reusable GOV.UK outline secondary action button */
@Suppress("FunctionNaming")
@Composable
fun SecondaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    GdsButtonFrame(
        onClick = onClick,
        enabled = enabled,
        colors =
            GdsButtonColors(
                fill = MmoColors.White,
                pressedFill = MmoColors.Background,
                content = if (enabled) MmoColors.Text else MmoColors.Grey1,
                border = if (enabled) MmoColors.Text else MmoColors.Grey1,
            ),
        modifier = modifier.fillMaxWidth(),
    ) { contentColor ->
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, color = contentColor),
        )
    }
}

/** GOV.UK table row status tag. Text always carries the label (WCAG 1.4.1) — colour is supplementary only. */
@Suppress("FunctionNaming")
@Composable
fun StatusTag(
    status: RecordStatusTag,
    modifier: Modifier = Modifier,
) {
    val (bg, textCol) =
        when (status) {
            RecordStatusTag.Draft -> MmoColors.Background to MmoColors.Text
            RecordStatusTag.CompletePending -> Color(0xFFFFF5CC) to Color(0xFF6F5200)
            RecordStatusTag.Submitted -> Color(0xFFDFEFE5) to Color(0xFF005A30)
            RecordStatusTag.Amended -> Color(0xFFE1EDF7) to MmoColors.GovBlueHover
        }

    Surface(
        color = bg,
        shape = RoundedCornerShape(2.dp),
        modifier = modifier,
    ) {
        Text(
            text = stringResource(status.labelRes),
            style =
                MaterialTheme.typography.labelLarge.copy(
                    color = textCol,
                    fontWeight = Normal,
                    fontSize = 13.sp,
                ),
            modifier = Modifier.padding(horizontal = Spacing.xxs, vertical = 2.dp),
        )
    }
}

/** GOV.UK Expandable details disclosure widget (accordions) */
@Suppress("FunctionNaming")
@Composable
fun ExpandableDetails(
    title: String,
    modifier: Modifier = Modifier,
    titleFontWeight: FontWeight = Normal,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        label = "arrowRotation",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .clickable { expanded = !expanded }
                    .fillMaxWidth()
                    .heightIn(min = Spacing.minTouchTarget)
                    .padding(vertical = Spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CustomPlayArrowIcon(
                tint = MmoColors.GovBlue,
                modifier =
                    Modifier
                        .size(12.dp)
                        .rotate(rotation),
            )
            Spacer(modifier = Modifier.width(Spacing.xs))
            Text(
                text = title,
                style =
                    MaterialTheme.typography.bodyLarge.copy(
                        color = MmoColors.GovBlue,
                        fontWeight = titleFontWeight,
                        textDecoration = TextDecoration.Underline,
                    ),
            )
        }

        if (expanded) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            // Vertical gray border on the left inside the expanded section
                            drawLine(
                                color = MmoColors.Grey2,
                                start = Offset(0f, 0f),
                                end = Offset(0f, size.height),
                                strokeWidth = 8f,
                            )
                        }.padding(start = Spacing.m, top = Spacing.xxs, bottom = Spacing.xs),
            ) {
                content()
            }
        }
    }
}

/**
 * Shared, centred Royal Crest footer shown at the bottom of every Home-shell tab (Home, Notifications,
 * Settings) and every Catch Record wizard screen — see [uk.gov.defra.mmocatchrecord.feature.home.presentation.HomeScreen]
 * and [uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow.CatchRecordWizardScaffold].
 */
@Suppress("FunctionNaming")
@Composable
fun RoyalCrestFooter(
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.m)
                .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        RoyalCrestPlaceholder()
    }
}

/** Stable, localisation-independent lookups for [MmoBottomNavigationBar]'s tabs (tests should prefer these). */
object MmoBottomNavigationBarTestTags {
    const val HOME_TAB = "mmo_bottom_nav_home_tab"
    const val NOTIFICATIONS_TAB = "mmo_bottom_nav_notifications_tab"
    const val SETTINGS_TAB = "mmo_bottom_nav_settings_tab"
}

/** App bottom navigation bar matching the iOS Design */
@Suppress("FunctionNaming")
@Composable
fun MmoBottomNavigationBar(
    selectedItem: Int,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MmoColors.White,
        modifier =
            modifier
                .fillMaxWidth()
                .drawBehind {
                    // Top border divider line
                    drawLine(
                        color = MmoColors.Grey3,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 2f,
                    )
                },
    ) {
        Row(
            modifier =
                Modifier
                    .navigationBarsPadding()
                    .fillMaxWidth()
                    .height(64.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Home tab
            BottomNavItem(
                selected = selectedItem == 0,
                label = stringResource(R.string.nav_home),
                iconComposable = { col -> CustomHomeIcon(tint = col, modifier = Modifier.size(24.dp)) },
                onClick = { onItemClick(0) },
                modifier = Modifier.testTag(MmoBottomNavigationBarTestTags.HOME_TAB),
            )

            // Notifications tab
            BottomNavItem(
                selected = selectedItem == 1,
                label = stringResource(R.string.nav_notifications),
                iconComposable = { col -> CustomNotificationsIcon(tint = col, modifier = Modifier.size(24.dp)) },
                onClick = { onItemClick(1) },
                modifier = Modifier.testTag(MmoBottomNavigationBarTestTags.NOTIFICATIONS_TAB),
            )

            // Settings tab
            BottomNavItem(
                selected = selectedItem == 2,
                label = stringResource(R.string.nav_settings),
                iconComposable = { col -> CustomSettingsIcon(tint = col, modifier = Modifier.size(24.dp)) },
                onClick = { onItemClick(2) },
                modifier = Modifier.testTag(MmoBottomNavigationBarTestTags.SETTINGS_TAB),
            )
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun BottomNavItem(
    onClick: () -> Unit,
    iconComposable: @Composable (Color) -> Unit,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val col = if (selected) MmoColors.GovBlue else MmoColors.Grey1

    Column(
        modifier =
            modifier
                .clickable(onClick = onClick)
                .widthIn(min = Spacing.minTouchTarget)
                .fillMaxHeight(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        iconComposable(col)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style =
                MaterialTheme.typography.labelLarge.copy(
                    fontSize = 11.sp,
                    color = col,
                    fontWeight = if (selected) FontWeight.Bold else Normal,
                ),
        )
    }
}
