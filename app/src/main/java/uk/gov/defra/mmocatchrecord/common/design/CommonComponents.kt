package uk.gov.defra.mmocatchrecord.common.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.feature.home.domain.CatchRecordStatus

/** GOV.UK Important Notification Banner */
@Composable
fun ImportantNotificationBanner(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .border(width = 1.dp, color = MmoColors.GovBlue),
    ) {
        // Banner header
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MmoColors.GovBlue)
                    .padding(horizontal = Spacing.s, vertical = Spacing.xxs),
        ) {
            Text(
                text = title,
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        color = MmoColors.White,
                        fontWeight = FontWeight.Bold,
                    ),
            )
        }
        // Banner content box
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MmoColors.White)
                    .drawBehind {
                        // Thick blue left border on inside content
                        drawLine(
                            color = MmoColors.GovBlue,
                            start = Offset(0f, 0f),
                            end = Offset(0f, size.height),
                            strokeWidth = 12f,
                        )
                    }.padding(start = Spacing.m, top = Spacing.s, end = Spacing.s, bottom = Spacing.s),
        ) {
            Text(
                text = message,
                style =
                    MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MmoColors.Text,
                    ),
            )
        }
    }
}

/** Reusable GOV.UK green primary action button */
@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(0.dp), // sharp square corners per GDS
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MmoColors.SuccessGreen,
                contentColor = MmoColors.White,
                disabledContainerColor = MmoColors.Grey2,
                disabledContentColor = MmoColors.Grey1,
            ),
        contentPadding = PaddingValues(horizontal = Spacing.m, vertical = Spacing.s),
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = Spacing.minTouchTarget)
                .drawBehind {
                    // Subtle 3dp solid dark shadow/border on bottom to match GDS button style
                    if (enabled) {
                        drawRect(
                            color = Color(0xFF005A30),
                            topLeft = Offset(0f, size.height - 4.dp.toPx()),
                            size = Size(size.width, 4.dp.toPx()),
                        )
                    }
                },
    ) {
        Text(
            text = text,
            style =
                MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MmoColors.White,
                ),
        )
    }
}

/** GOV.UK table row status tag */
@Composable
fun StatusTag(
    status: CatchRecordStatus,
    modifier: Modifier = Modifier,
) {
    val (bg, textCol, labelRes) =
        when (status) {
            CatchRecordStatus.SUBMITTED -> Triple(Color(0xFFDFEFE5), Color(0xFF005A30), R.string.status_submitted)
            CatchRecordStatus.AMENDED -> Triple(Color(0xFFE1EDF7), MmoColors.GovBlue, R.string.status_amended)
            CatchRecordStatus.UNSENT -> Triple(Color(0xFFFFF5CC), Color(0xFF6F5200), R.string.status_unsent)
            CatchRecordStatus.LATE -> Triple(Color(0xFFFCE1E1), Color(0xFF942514), R.string.status_late)
        }

    Surface(
        color = bg,
        shape = RoundedCornerShape(2.dp),
        modifier = modifier,
    ) {
        Text(
            text = stringResource(labelRes),
            style =
                MaterialTheme.typography.labelLarge.copy(
                    color = textCol,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                ),
            modifier = Modifier.padding(horizontal = Spacing.xxs, vertical = 2.dp),
        )
    }
}

/** GOV.UK style Pagination Bar */
@Composable
fun PaginationBar(
    pageStart: Int,
    pageEnd: Int,
    totalCount: Int,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.s),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Info text
        Text(
            text = stringResource(R.string.showing_x_to_y_of_z, pageStart, pageEnd, totalCount),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
        )

        // Page square "1" indicator
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .background(MmoColors.GovBlue),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "1",
                color = MmoColors.White,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            )
        }

        // "Next" link with arrow
        Row(
            modifier =
                Modifier
                    .clickable(onClick = onNextClick)
                    .heightIn(min = Spacing.minTouchTarget)
                    .padding(Spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.next),
                color = MmoColors.GovBlue,
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        textDecoration = TextDecoration.Underline,
                    ),
            )
            Spacer(modifier = Modifier.width(Spacing.xxs))
            CustomArrowForwardIcon(
                tint = MmoColors.GovBlue,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** GOV.UK Expandable details disclosure widget (accordions) */
@Composable
fun ExpandableDetails(
    title: String,
    modifier: Modifier = Modifier,
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
                        fontWeight = FontWeight.Bold,
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
                            // Vertical grey border on the left inside the expanded section
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

/** App bottom navigation bar matching the iOS Design */
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
            )

            // Notifications tab
            BottomNavItem(
                selected = selectedItem == 1,
                label = stringResource(R.string.nav_notifications),
                iconComposable = { col -> CustomNotificationsIcon(tint = col, modifier = Modifier.size(24.dp)) },
                onClick = { onItemClick(1) },
            )

            // Settings tab
            BottomNavItem(
                selected = selectedItem == 2,
                label = stringResource(R.string.nav_settings),
                iconComposable = { col -> CustomSettingsIcon(tint = col, modifier = Modifier.size(24.dp)) },
                onClick = { onItemClick(2) },
            )
        }
    }
}

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
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                ),
        )
    }
}
