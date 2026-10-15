package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse

class WizardGearUseBindingTests {
    private val gearOne = GearUse("gear-use-1", "gear-seine-nets", statisticalSubRectangleCode = "38E95")
    private val gearTwo = GearUse("gear-use-2", "gear-seine-nets", statisticalSubRectangleCode = null)
    private val draft =
        CatchRecordDraft(
            id = "draft-1",
            vesselId = "vessel-1",
            gearUses = listOf(gearOne, gearTwo),
            modifiedAtEpochMillis = 0L,
            status = DraftStatus.Draft,
        )
    private val nextPending: (CatchRecordDraft) -> GearUse? =
        { it.gearUses.firstOrNull { gear -> gear.statisticalSubRectangleCode == null } }

    @Test
    fun `edit id wins and does not fall back`() {
        assertEquals(gearOne, resolveWizardGearUse(draft, "gear-use-1", "gear-use-2", nextPending))
        assertNull(resolveWizardGearUse(draft, "missing", "gear-use-2", nextPending))
    }

    @Test
    fun `bound id resolves a completed gear before the next pending gear`() {
        assertEquals(gearOne, resolveWizardGearUse(draft, null, "gear-use-1", nextPending))
    }

    @Test
    fun `a stale bound id is authoritative and never retargets another gear`() {
        assertNull(resolveWizardGearUse(draft, null, "removed", nextPending))
    }

    @Test
    fun `no binding falls back to the next pending gear`() {
        assertEquals(gearTwo, resolveWizardGearUse(draft, null, null, nextPending))
        assertNull(resolveWizardGearUse(null, null, null, nextPending))
    }

    @Test
    fun `edit route for species search keeps the edit context`() {
        assertEquals(
            GearSpeciesSearchRoute(editGearUseId = "g"),
            editRouteFor(WizardStep.GearSpeciesSearch, "g"),
        )
    }

    @Test
    fun `gear route for binds species steps and delegates others`() {
        assertEquals(GearSpeciesSearchRoute(gearUseId = "g"), gearRouteFor(WizardStep.GearSpeciesSearch, "g"))
        assertEquals(GearSpeciesChecklistRoute(gearUseId = "g"), gearRouteFor(WizardStep.GearSpeciesChecklist, "g"))
        assertEquals(CheckYourAnswersRoute, gearRouteFor(WizardStep.CheckYourAnswers, "g"))
    }
}
