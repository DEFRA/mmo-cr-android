@file:Suppress("detekt.FunctionNaming", "detekt.MaxLineLength", "detekt.TooManyFunctions")

package uk.gov.defra.mmocatchrecord.feature.home.presentation

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
    const val PAGINATION_PREVIOUS = "catch_records_pagination_previous"
    const val PAGINATION_NEXT = "catch_records_pagination_next"
    const val PAGINATION_PAGE = "catch_records_pagination_page"
    const val PAGINATION_SHOWING = "catch_records_pagination_showing"

    fun row(id: String) = "catch_record_row_$id"

    fun retry(id: String) = "catch_record_retry_$id"

    fun createdBy(id: String) = "catch_record_created_by_$id"
}

/** Records per page shown in the table — this is local, in-memory windowing, not a DB page query. */
private const val CATCH_RECORDS_PAGE_SIZE = 10

/** Column widths/gap sized to fit a ~412dp-wide phone without scrolling (the reference design's implied
 * viewport); narrower/zoomed viewports fall back to [horizontalScrollState] per WCAG 1.4.10 — see ADR-0014
 * Phase F. */
private val TripEndDateColumnWidth: Dp = 95.dp
private val VesselColumnWidth: Dp = 85.dp
private val StatusColumnWidth: Dp = 100.dp
private val CreatedByColumnWidth: Dp = 62.dp
private val TableColumnGap: Dp = Spacing.xs

/** 4-column table, reversing ADR-0014 Phase B's stacked rows per an explicit product decision — see ADR-0014
 * Phase F. Each row/header stays a distinct `LazyColumn` item (R14) so off-screen rows remain scrollable-to
 * by index; [horizontalScrollState] is shared across them so the columns stay aligned when scrolled. */
fun LazyListScope.catchRecordsListSection(
    records: List<CatchRecordSummary>,
    onRecordClick: (String) -> Unit,
    onRetry: (String) -> Unit = {},
    retryingIds: Set<String> = emptySet(),
    currentPage: Int = 0,
    onPageChange: (Int) -> Unit = {},
    horizontalScrollState: ScrollState = ScrollState(0),
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
    val totalPages = ((records.size - 1) / CATCH_RECORDS_PAGE_SIZE + 1).coerceAtLeast(1)
    val page = currentPage.coerceIn(0, totalPages - 1)
    val startIndex = page * CATCH_RECORDS_PAGE_SIZE
    val endIndexExclusive = (startIndex + CATCH_RECORDS_PAGE_SIZE).coerceAtMost(records.size)
    val pageRecords = records.subList(startIndex, endIndexExclusive)

    item {
        Column {
            CatchRecordsTableHeaderRow(horizontalScrollState)
            HorizontalDivider(color = MmoColors.Grey3)
        }
    }
    items(pageRecords, key = { it.id }) { record ->
        Column {
            CatchRecordTableRow(
                record = record,
                horizontalScrollState = horizontalScrollState,
                onClick = onRecordClick,
                onRetry = onRetry,
                isRetrying = record.id in retryingIds,
            )
            HorizontalDivider(color = MmoColors.Grey3)
        }
    }
    item {
        CatchRecordsPaginationBar(
            page = page,
            totalPages = totalPages,
            rangeStart = startIndex + 1,
            rangeEnd = endIndexExclusive,
            totalRecords = records.size,
            onPageChange = onPageChange,
        )
    }
}

private fun formatDmyDate(date: DmyDate): String = "%02d/%02d/%04d".format(date.day, date.month, date.year)

@Suppress("FunctionNaming")
@Composable
private fun CatchRecordsTableHeaderRow(horizontalScrollState: ScrollState) {
    Row(modifier = Modifier.horizontalScroll(horizontalScrollState).padding(vertical = Spacing.s)) {
        TableHeaderCell(stringResource(R.string.col_trip_end_date), TripEndDateColumnWidth)
        TableHeaderCell(stringResource(R.string.col_vessel), VesselColumnWidth)
        TableHeaderCell(stringResource(R.string.col_status), StatusColumnWidth)
        TableHeaderCell(stringResource(R.string.col_created_by), CreatedByColumnWidth, isLastColumn = true)
    }
}

@Suppress("FunctionNaming")
@Composable
private fun TableHeaderCell(
    text: String,
    width: Dp,
    isLastColumn: Boolean = false,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, color = MmoColors.Text),
        modifier =
            Modifier
                .width(width)
                .padding(end = if (isLastColumn) Spacing.none else TableColumnGap)
                .semantics { heading() },
    )
}

/** Trip-end-date is a real link only for a resumable row; an always-link non-resumable row would be a
 * misleading affordance (see ADR-0014 Phase F — deviation from the reference image's uniform link styling). */
@Suppress("FunctionNaming")
@Composable
private fun CatchRecordTableRow(
    record: CatchRecordSummary,
    horizontalScrollState: ScrollState,
    onClick: (String) -> Unit,
    onRetry: (String) -> Unit,
    isRetrying: Boolean,
) {
    val isResumable = record.status == RecordStatusTag.Draft || record.status == RecordStatusTag.ReadyToSubmit
    val minTouchTarget = LocalMinimumInteractiveComponentSize.current
    val tripEndDateText =
        record.tripEndDate?.let(::formatDmyDate) ?: stringResource(R.string.records_trip_end_date_not_set)
    val createdByLabel = stringResource(R.string.records_created_by_you)

    Row(
        modifier =
            Modifier
                .horizontalScroll(horizontalScrollState)
                .sizeIn(minHeight = minTouchTarget)
                .testTag(CatchRecordsListSectionTestTags.row(record.id))
                .padding(vertical = Spacing.s),
    ) {
        TripEndDateCell(tripEndDateText, isResumable, minTouchTarget) { onClick(record.id) }
        Text(
            text = record.vesselId,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.width(VesselColumnWidth).padding(end = TableColumnGap),
        )
        StatusCell(record, isRetrying, minTouchTarget) { onRetry(record.id) }
        Text(
            text = createdByLabel,
            style = MaterialTheme.typography.bodyLarge,
            modifier =
                Modifier
                    .width(CreatedByColumnWidth)
                    .testTag(CatchRecordsListSectionTestTags.createdBy(record.id)),
        )
    }
}

@Suppress("FunctionNaming")
@Composable
private fun TripEndDateCell(
    text: String,
    isResumable: Boolean,
    minTouchTarget: Dp,
    onClick: () -> Unit,
) {
    Box(modifier = Modifier.width(TripEndDateColumnWidth).padding(end = TableColumnGap)) {
        if (isResumable) {
            Text(
                text = text,
                style =
                    MaterialTheme.typography.bodyLarge.copy(
                        color = MmoColors.Link,
                        textDecoration = TextDecoration.Underline,
                    ),
                modifier =
                    Modifier
                        .sizeIn(minWidth = minTouchTarget, minHeight = minTouchTarget)
                        .clickable(role = Role.Button, onClick = onClick),
            )
        } else {
            Text(text = text, style = MaterialTheme.typography.bodyLarge.copy(color = MmoColors.Text))
        }
    }
}

@Suppress("FunctionNaming")
@Composable
private fun StatusCell(
    record: CatchRecordSummary,
    isRetrying: Boolean,
    minTouchTarget: Dp,
    onRetry: () -> Unit,
) {
    Column(modifier = Modifier.width(StatusColumnWidth).padding(end = TableColumnGap)) {
        StatusTag(status = record.status)
        if (record.status == RecordStatusTag.AwaitingSync) {
            OutlinedButton(
                onClick = onRetry,
                enabled = !isRetrying,
                modifier =
                    Modifier
                        .padding(top = Spacing.xxs)
                        .sizeIn(minWidth = minTouchTarget, minHeight = minTouchTarget)
                        .testTag(CatchRecordsListSectionTestTags.retry(record.id)),
            ) {
                val label = if (isRetrying) R.string.records_retry_pending else R.string.records_retry_action
                Text(stringResource(label))
            }
        }
    }
}

/** Real "Showing X to Y of Z" + Previous/Next, wired to page state and disabled (not just unstyled) at the
 * first/last page — see ADR-0014 Phase F. */
@Suppress("FunctionNaming")
@Composable
private fun CatchRecordsPaginationBar(
    page: Int,
    totalPages: Int,
    rangeStart: Int,
    rangeEnd: Int,
    totalRecords: Int,
    onPageChange: (Int) -> Unit,
) {
    val pageContentDescription =
        stringResource(R.string.pagination_page_content_description, page + 1) + " / $totalPages"
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PaginationLink(
            text = stringResource(R.string.pagination_previous),
            enabled = page > 0,
            onClick = { onPageChange(page - 1) },
            testTag = CatchRecordsListSectionTestTags.PAGINATION_PREVIOUS,
        )
        Text(
            text = stringResource(R.string.pagination_showing, rangeStart, rangeEnd, totalRecords),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            modifier =
                Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.s)
                    .testTag(CatchRecordsListSectionTestTags.PAGINATION_SHOWING),
        )
        Box(
            modifier =
                Modifier
                    .sizeIn(minWidth = Spacing.minTouchTarget, minHeight = Spacing.minTouchTarget)
                    .background(MmoColors.GovBlue)
                    .padding(horizontal = Spacing.xs, vertical = Spacing.xxs)
                    .semantics { contentDescription = pageContentDescription }
                    .testTag(CatchRecordsListSectionTestTags.PAGINATION_PAGE),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "${page + 1}",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, color = MmoColors.White),
            )
        }
        PaginationLink(
            text = stringResource(R.string.pagination_next),
            enabled = page < totalPages - 1,
            onClick = { onPageChange(page + 1) },
            testTag = CatchRecordsListSectionTestTags.PAGINATION_NEXT,
            modifier = Modifier.padding(start = Spacing.s),
        )
    }
}

@Suppress("FunctionNaming")
@Composable
private fun PaginationLink(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val color = if (enabled) MmoColors.Link else MmoColors.Grey2
    val baseModifier =
        modifier
            .sizeIn(minWidth = Spacing.minTouchTarget, minHeight = Spacing.minTouchTarget)
            .testTag(testTag)
    val finalModifier =
        if (enabled) {
            baseModifier.clickable(role = Role.Button, onClick = onClick)
        } else {
            baseModifier.semantics { disabled() }
        }
    Text(
        text = text,
        style =
            MaterialTheme.typography.bodyLarge.copy(
                color = color,
                textDecoration = if (enabled) TextDecoration.Underline else TextDecoration.None,
                fontWeight = FontWeight.Bold,
            ),
        modifier = finalModifier,
    )
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

@Preview(name = "Catch record table row", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Suppress("FunctionNaming")
@Composable
fun CatchRecordTableRowPreview() {
    val scrollState = ScrollState(0)
    MmoTheme {
        Column {
            CatchRecordsTableHeaderRow(scrollState)
            HorizontalDivider(color = MmoColors.Grey3)
            CatchRecordTableRow(
                record = previewCatchRecords[3],
                horizontalScrollState = scrollState,
                onClick = {},
                onRetry = {},
                isRetrying = false,
            )
        }
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
