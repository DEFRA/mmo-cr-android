package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.MeasurementValue
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldKeys
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearType

/** JVM coverage for the state-independent [RemoveGearScreenContent]. */
@RunWith(RobolectricTestRunner::class)
class RemoveGearScreenTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val pots = GearType(id = "gear-pots", name = "Pots (FPO)")
    private val purseSeine = GearType(id = "gear-ps2", name = "Two boat operated purse seine (PS2)")
    private val gearTypes = listOf(pots, purseSeine)

    private val potsUse = GearUse(id = "gear-use-1", gearTypeId = pots.id, statisticalSubRectangleCode = null)
    private val purseSeineUse =
        GearUse(
            id = "gear-use-2",
            gearTypeId = purseSeine.id,
            statisticalSubRectangleCode = null,
            measurements = mapOf(GearMeasurementFieldKeys.MESH_SIZE_MM to MeasurementValue.Numeric(20.0, "mm")),
        )

    private val draft =
        CatchRecordDraft(
            id = "draft-1",
            vesselId = "vessel-1",
            gearUses = listOf(potsUse, purseSeineUse),
            modifiedAtEpochMillis = 0L,
            status = DraftStatus.Draft,
        )

    private fun setContent(onRemove: (CatchRecordDraft) -> Unit = {}) {
        composeTestRule.setContent {
            MmoTheme {
                RemoveGearScreenContent(draft = draft, gearTypes = gearTypes, onRemove = onRemove)
            }
        }
    }

    @Test
    fun displaysEachGearWithItsMeshSizeLabel() {
        setContent()

        composeTestRule.onNodeWithText("Pots").assertIsDisplayed()
        composeTestRule.onNodeWithText("Two boat operated purse seine - (mesh size 20 mm)").assertIsDisplayed()
    }

    @Test
    fun continuingWithNothingCheckedShowsAnErrorAndDoesNotRemove() {
        var removed: CatchRecordDraft? = null
        setContent { removed = it }

        composeTestRule.onNodeWithTag(RemoveGearScreenTestTags.CONTINUE_ACTION).performClick()

        composeTestRule
            .onNodeWithTag(RemoveGearScreenTestTags.ERROR_MESSAGE)
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        assertNull(removed)
    }

    @Test
    fun continuingRemovesOnlyCheckedGear() {
        var removed: CatchRecordDraft? = null
        setContent { removed = it }

        composeTestRule.onNodeWithTag("${RemoveGearScreenTestTags.CHECKBOX_PREFIX}_0").performClick()
        composeTestRule.onNodeWithTag(RemoveGearScreenTestTags.CONTINUE_ACTION).performClick()

        assertEquals(listOf("gear-use-2"), removed?.gearUses?.map { it.id })
    }

    @Test
    fun labelFallsBackToTheRawGearTypeIdWhenTheGearTypeIsUnknown() {
        assertEquals("gear-pots", removeGearLabelFor(null, potsUse))
    }
}
