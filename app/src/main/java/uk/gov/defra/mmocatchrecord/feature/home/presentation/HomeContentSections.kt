package uk.gov.defra.mmocatchrecord.feature.home.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.common.design.ExpandableDetails
import uk.gov.defra.mmocatchrecord.common.design.ImportantNotificationBanner
import uk.gov.defra.mmocatchrecord.common.design.MmoColors
import uk.gov.defra.mmocatchrecord.common.design.PaginationBar
import uk.gov.defra.mmocatchrecord.common.design.PrimaryActionButton
import uk.gov.defra.mmocatchrecord.common.design.Spacing
import uk.gov.defra.mmocatchrecord.common.design.StatusTag
import uk.gov.defra.mmocatchrecord.feature.home.domain.HomeSummary

@Composable
fun ImportantBannerSection() {
    ImportantNotificationBanner(
        title = stringResource(R.string.important),
        message = stringResource(R.string.home_banner_text),
    )
}

@Composable
fun HeadingSection() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text(
            text = stringResource(R.string.your_catch_records),
            style = MaterialTheme.typography.headlineLarge.copy(color = MmoColors.Text),
        )

        PrimaryActionButton(
            text = stringResource(R.string.create_catch_record),
            onClick = { /* Navigate to create in later stages */ },
        )

        Text(
            text = stringResource(R.string.edit_trips_hint),
            style = MaterialTheme.typography.bodyLarge.copy(color = MmoColors.Text),
        )
    }
}

@Composable
fun LoadingIndicator() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = MmoColors.GovBlue)
    }
}

@Composable
fun CatchRecordsTableSection(summary: HomeSummary) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .border(1.dp, MmoColors.Grey3),
    ) {
        TableHeaderRow()
        TableContentRows(summary = summary)
    }

    PaginationBar(
        pageStart = summary.pageStart,
        pageEnd = summary.pageEnd,
        totalCount = summary.totalCount,
        onNextClick = { /* No-op in stub stage */ },
    )
}

@Composable
private fun TableHeaderRow() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MmoColors.Background)
                .padding(vertical = Spacing.s, horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.col_trip_end_date),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.weight(0.28f),
        )
        Text(
            text = stringResource(R.string.col_vessel),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.weight(0.24f),
        )
        Text(
            text = stringResource(R.string.col_status),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.weight(0.28f),
        )
        Text(
            text = stringResource(R.string.col_created_by),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.weight(0.20f),
        )
    }
}

@Composable
private fun TableContentRows(summary: HomeSummary) {
    summary.catchRecords.forEach { record ->
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        drawLine(
                            color = MmoColors.Grey3,
                            start =
                                androidx.compose.ui.geometry
                                    .Offset(0f, size.height),
                            end =
                                androidx.compose.ui.geometry
                                    .Offset(size.width, size.height),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }.padding(vertical = Spacing.s, horizontal = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = record.tripEndDate,
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        color = MmoColors.Link,
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                    ),
                modifier = Modifier.weight(0.28f),
            )
            Text(
                text = record.vesselName,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(0.24f),
            )
            Box(modifier = Modifier.weight(0.28f)) {
                StatusTag(status = record.status)
            }
            Text(
                text = record.createdBy,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(0.20f),
            )
        }
    }
}

@Composable
fun HelpAccordionsSection() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        HelpRecordingAccordion()
        CatchStatusesAccordion()
    }
}

@Composable
private fun HelpRecordingAccordion() {
    ExpandableDetails(title = stringResource(R.string.help_with_catch_recording)) {
        Text(
            text = stringResource(R.string.help_need_to_do),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = Spacing.xs),
        )
        Text(
            text = stringResource(R.string.help_need_to_do_body),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = Spacing.m),
        )
        Text(
            text = stringResource(R.string.help_when_create),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = Spacing.xs),
        )
        Text(
            text = stringResource(R.string.help_when_create_body),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = Spacing.xs),
        )

        val bulletIds = listOf(R.string.help_bullet_1, R.string.help_bullet_2, R.string.help_bullet_3)
        bulletIds.forEach { bulletRes ->
            Text(
                text = "• " + stringResource(bulletRes),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = Spacing.xxs),
            )
        }

        Text(
            text = stringResource(R.string.help_create_within),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.m),
        )
        Text(
            text = stringResource(R.string.help_special_cases),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = Spacing.xs),
        )
        Text(
            text = stringResource(R.string.help_special_cases_body),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = Spacing.m),
        )
        Text(
            text = stringResource(R.string.help_get_help),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = Spacing.xs),
        )
        Text(
            text = stringResource(R.string.help_get_help_body),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun CatchStatusesAccordion() {
    ExpandableDetails(title = stringResource(R.string.catch_record_statuses)) {
        StatusHelpRow(
            title = stringResource(R.string.status_unsent_title),
            desc = stringResource(R.string.status_unsent_desc),
        )
        StatusHelpRow(
            title = stringResource(R.string.status_submitted_title),
            desc = stringResource(R.string.status_submitted_desc),
        )
        StatusHelpRow(
            title = stringResource(R.string.status_amended_title),
            desc = stringResource(R.string.status_amended_desc),
        )
        StatusHelpRow(
            title = stringResource(R.string.status_late_title),
            desc = stringResource(R.string.status_late_desc),
        )
        Text(
            text = stringResource(R.string.status_check_tab),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = Spacing.s),
        )
    }
}

@Composable
fun StatusHelpRow(
    title: String,
    desc: String,
) {
    Column(modifier = Modifier.padding(bottom = Spacing.xs)) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
        Text(text = desc, style = MaterialTheme.typography.bodyMedium)
    }
}
