@file:Suppress("detekt.FunctionNaming", "detekt.MaxLineLength", "detekt.TooManyFunctions")

package uk.gov.defra.mmocatchrecord.feature.home.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.common.design.ExpandableDetails
import uk.gov.defra.mmocatchrecord.common.design.ImportantNotificationBanner
import uk.gov.defra.mmocatchrecord.common.design.MmoColors
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.common.design.PrimaryActionButton
import uk.gov.defra.mmocatchrecord.common.design.RecordStatusTag
import uk.gov.defra.mmocatchrecord.common.design.Spacing
import uk.gov.defra.mmocatchrecord.common.design.StatusTag
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DmyDate
import uk.gov.defra.mmocatchrecord.feature.home.domain.CatchRecordSummary

@Suppress("FunctionNaming")
@Composable
fun ImportantBannerSection() {
    ImportantNotificationBanner(
        title = stringResource(R.string.important),
        message = stringResource(R.string.home_banner_text),
    )
}

@Suppress("FunctionNaming")
@Composable
fun HeadingSection(onCreateCatchRecord: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text(
            text = stringResource(R.string.your_catch_records),
            style = MaterialTheme.typography.headlineLarge.copy(color = MmoColors.Text),
        )

        PrimaryActionButton(
            text = stringResource(R.string.create_catch_record),
            onClick = onCreateCatchRecord,
        )

        Text(
            text = stringResource(R.string.edit_trips_hint),
            style = MaterialTheme.typography.bodyLarge.copy(color = MmoColors.Text),
        )
    }
}

@Suppress("FunctionNaming")
@Composable
fun LoadingIndicator() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = MmoColors.GovBlue)
    }
}

object CatchRecordsListSectionTestTags {
    const val EMPTY_STATE = "catch_records_empty_state"
    const val COUNT = "catch_records_count"

    fun row(id: String) = "catch_record_row_$id"

    fun retry(id: String) = "catch_record_retry_$id"
}

/**
 * Stacked rows (ADR-0014 Phase B); must be called inside the caller's `LazyColumn` (R14). [onRetry] is
 * wired to FR8 manual retry (Phase C); [retryingIds] disables a row's button while its retry is pending.
 */
fun LazyListScope.catchRecordsListSection(
    records: List<CatchRecordSummary>,
    onRecordClick: (String) -> Unit,
    onRetry: (String) -> Unit = {},
    retryingIds: Set<String> = emptySet(),
) {
    if (records.isEmpty()) {
        item {
            Text(
                text = stringResource(R.string.no_catch_records_yet),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag(CatchRecordsListSectionTestTags.EMPTY_STATE),
            )
        }
        return
    }
    items(records, key = { it.id }) { record ->
        CatchRecordRow(
            record = record,
            onClick = onRecordClick,
            onRetry = onRetry,
            isRetrying = record.id in retryingIds,
        )
    }
    item {
        Text(
            text = stringResource(R.string.catch_records_count, records.size),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(vertical = Spacing.s).testTag(CatchRecordsListSectionTestTags.COUNT),
        )
    }
}

private fun formatDmyDate(date: DmyDate): String = "%02d/%02d/%04d".format(date.day, date.month, date.year)

@Suppress("FunctionNaming")
@Composable
private fun CatchRecordRow(
    record: CatchRecordSummary,
    onClick: (String) -> Unit,
    onRetry: (String) -> Unit,
    isRetrying: Boolean,
) {
    val isResumable = record.status == RecordStatusTag.Draft || record.status == RecordStatusTag.ReadyToSubmit
    val minTouchTarget = LocalMinimumInteractiveComponentSize.current
    val reference = record.catchRecordReference ?: stringResource(R.string.records_reference_pending)
    val tripEndDateText =
        record.tripEndDate?.let(::formatDmyDate) ?: stringResource(R.string.records_trip_end_date_not_set)
    val rowModifier =
        Modifier
            .fillMaxWidth()
            .sizeIn(minWidth = minTouchTarget, minHeight = minTouchTarget)
            .testTag(CatchRecordsListSectionTestTags.row(record.id))
            .let { base -> if (isResumable) base.clickable { onClick(record.id) } else base }
            .padding(vertical = Spacing.s)

    Column(modifier = rowModifier, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Text(
            text = reference,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = "${stringResource(R.string.col_vessel)}: ${record.vesselId}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "${stringResource(R.string.col_trip_end_date)}: $tripEndDateText",
            style = MaterialTheme.typography.bodyMedium,
        )
        StatusTag(status = record.status, modifier = Modifier.padding(top = Spacing.xxs))
        if (record.status == RecordStatusTag.AwaitingSync) {
            OutlinedButton(
                onClick = { onRetry(record.id) },
                enabled = !isRetrying,
                modifier =
                    Modifier
                        .sizeIn(minWidth = minTouchTarget, minHeight = minTouchTarget)
                        .testTag(CatchRecordsListSectionTestTags.retry(record.id)),
            ) {
                val label = if (isRetrying) R.string.records_retry_pending else R.string.records_retry_action
                Text(stringResource(label))
            }
        }
        HorizontalDivider(color = MmoColors.Grey3, modifier = Modifier.padding(top = Spacing.xs))
    }
}

@Suppress("FunctionNaming")
@Composable
fun HelpAccordionsSection() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        HelpRecordingAccordion()
        CatchStatusesAccordion()
    }
}

@Suppress("FunctionNaming")
@Composable
private fun HelpRecordingAccordion() {
    ExpandableDetails(title = stringResource(R.string.help_with_catch_recording)) {
        Text(
            stringResource(R.string.help_need_to_do),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = Spacing.xs),
        )
        Text(
            stringResource(R.string.help_need_to_do_body),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = Spacing.m),
        )
        Text(
            stringResource(R.string.help_when_create),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = Spacing.xs),
        )
        Text(
            stringResource(R.string.help_when_create_body),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = Spacing.xs),
        )
        listOf(R.string.help_bullet_1, R.string.help_bullet_2, R.string.help_bullet_3).forEach { bulletRes ->
            Text(
                text = "Ã¢â‚¬Â¢ " + stringResource(bulletRes),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = Spacing.xxs),
            )
        }
        Text(
            stringResource(R.string.help_create_within),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.m),
        )
        Text(
            stringResource(R.string.help_special_cases),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = Spacing.xs),
        )
        Text(
            stringResource(R.string.help_special_cases_body),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = Spacing.m),
        )
        Text(
            stringResource(R.string.help_get_help),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = Spacing.xs),
        )
        Text(stringResource(R.string.help_get_help_body), style = MaterialTheme.typography.bodyMedium)
    }
}

@Suppress("FunctionNaming")
@Composable
private fun CatchStatusesAccordion() {
    ExpandableDetails(title = stringResource(R.string.catch_record_statuses)) {
        StatusHelpRow(stringResource(R.string.status_draft_title), stringResource(R.string.status_draft_desc))
        StatusHelpRow(
            stringResource(R.string.status_ready_to_submit_title),
            stringResource(R.string.status_ready_to_submit_desc),
        )
        StatusHelpRow(
            stringResource(R.string.status_awaiting_sync_title),
            stringResource(R.string.status_awaiting_sync_desc),
        )
        StatusHelpRow(stringResource(R.string.status_submitted_title), stringResource(R.string.status_submitted_desc))
        Text(
            stringResource(R.string.status_check_tab),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = Spacing.s),
        )
    }
}

@Suppress("FunctionNaming")
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

private val previewCatchRecords =
    listOf(
        CatchRecordSummary("1", "MMO-REF-001", "ACHILLES", DmyDate(20, 11, 2020), RecordStatusTag.Submitted),
        CatchRecordSummary("2", "MMO-REF-002", "ACHILLES", DmyDate(18, 11, 2020), RecordStatusTag.ReadyToSubmit),
        CatchRecordSummary("3", null, "ACHILLES", null, RecordStatusTag.Draft),
        CatchRecordSummary("4", "MMO-REF-004", "ACHILLES", DmyDate(15, 11, 2020), RecordStatusTag.AwaitingSync),
    )

@Preview(name = "Important banner section", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Suppress("FunctionNaming")
@Composable
fun ImportantBannerSectionPreview() {
    MmoTheme {
        ImportantBannerSection()
    }
}

@Preview(name = "Heading section", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Suppress("FunctionNaming")
@Composable
fun HeadingSectionPreview() {
    MmoTheme {
        HeadingSection(onCreateCatchRecord = {})
    }
}

@Preview(name = "Loading indicator", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Suppress("FunctionNaming")
@Composable
fun LoadingIndicatorPreview() {
    MmoTheme {
        LoadingIndicator()
    }
}

@Preview(name = "Catch records list", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Suppress("FunctionNaming")
@Composable
fun CatchRecordsListSectionPreview() {
    MmoTheme {
        LazyColumn(modifier = Modifier.padding(Spacing.m)) {
            catchRecordsListSection(records = previewCatchRecords, onRecordClick = {})
        }
    }
}

@Preview(name = "Catch records list (empty)", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Suppress("FunctionNaming")
@Composable
fun CatchRecordsListSectionEmptyPreview() {
    MmoTheme {
        LazyColumn(modifier = Modifier.padding(Spacing.m)) {
            catchRecordsListSection(records = emptyList(), onRecordClick = {})
        }
    }
}

@Preview(name = "Catch record row", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Suppress("FunctionNaming")
@Composable
fun CatchRecordRowPreview() {
    MmoTheme {
        CatchRecordRow(
            record = previewCatchRecords[3],
            onClick = {},
            onRetry = {},
            isRetrying = false,
        )
    }
}

@Preview(name = "Help accordions section", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Suppress("FunctionNaming")
@Composable
fun HelpAccordionsSectionPreview() {
    MmoTheme {
        HelpAccordionsSection()
    }
}

@Preview(name = "Help recording accordion", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Suppress("FunctionNaming")
@Composable
fun HelpRecordingAccordionPreview() {
    MmoTheme {
        HelpRecordingAccordion()
    }
}

@Preview(name = "Catch statuses accordion", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Suppress("FunctionNaming")
@Composable
fun CatchStatusesAccordionPreview() {
    MmoTheme {
        CatchStatusesAccordion()
    }
}

@Preview(name = "Status help row", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Suppress("FunctionNaming")
@Composable
fun StatusHelpRowPreview() {
    MmoTheme {
        StatusHelpRow(
            title = stringResource(R.string.status_draft_title),
            desc = stringResource(R.string.status_draft_desc),
        )
    }
}
