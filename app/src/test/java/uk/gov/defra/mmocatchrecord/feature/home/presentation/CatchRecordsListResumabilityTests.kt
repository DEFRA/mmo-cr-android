package uk.gov.defra.mmocatchrecord.feature.home.presentation

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.common.design.RecordStatusTag
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DmyDate
import uk.gov.defra.mmocatchrecord.feature.home.domain.CatchRecordSummary

/** Only Draft rows resume; Complete/Pending, Submitted and Amended rows must not be clickable. */
@RunWith(RobolectricTestRunner::class)
class CatchRecordsListResumabilityTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val records =
        RecordStatusTag.entries.mapIndexed { index, status ->
            CatchRecordSummary("id-$index", "REF-$index", "v1", DmyDate(index + 1, 1, 2026), status, "ACHILLES")
        }

    @Config(qualifiers = "w412dp-h2000dp")
    @Test
    fun `only draft rows are resumable`() {
        composeTestRule.setContent {
            MmoTheme {
                LazyColumn {
                    catchRecordsListSection(records = records, onRecordClick = {})
                }
            }
        }

        records.forEach { record ->
            val row = hasTestTag(CatchRecordsListSectionTestTags.row(record.id))
            val clickableNodes =
                composeTestRule
                    .onAllNodes(hasClickAction() and (row or hasAnyAncestor(row)), useUnmergedTree = true)
                    .fetchSemanticsNodes()
            assertEquals(record.status.name, record.status == RecordStatusTag.Draft, clickableNodes.isNotEmpty())
        }
    }
}
