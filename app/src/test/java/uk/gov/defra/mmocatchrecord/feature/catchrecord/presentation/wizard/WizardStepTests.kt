package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DmyDate
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelection
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelectionMode

class WizardStepTests {
    private fun draft(
        isTripToday: Boolean? = true,
        departureDate: DmyDate? = null,
        returnDate: DmyDate? = null,
        departurePort: PortSelection? = PortSelection("port-1", PortSelectionMode.FirstTime),
        returnPort: PortSelection? = PortSelection("port-1", PortSelectionMode.FirstTime),
        gearUses: List<GearUse> = emptyList(),
    ) = CatchRecordDraft(
        id = "draft-1",
        vesselId = "vessel-1",
        isTripToday = isTripToday,
        departureDate = departureDate,
        returnDate = returnDate,
        departurePort = departurePort,
        returnPort = returnPort,
        gearUses = gearUses,
        modifiedAtEpochMillis = 0L,
    )

    private fun gearUse(
        id: String,
        confirmedUsedOnTrip: Boolean = false,
        statisticalSubRectangleCode: String? = null,
    ) = GearUse(
        id = id,
        gearTypeId = "gear-1",
        statisticalSubRectangleCode = statisticalSubRectangleCode,
        confirmedUsedOnTrip = confirmedUsedOnTrip,
    )

    @Test
    fun `no gear uses routes to gear search`() {
        assertEquals(WizardStep.GearSearch, nextWizardStepForDraft(draft(gearUses = emptyList())))
    }

    @Test
    fun `gear added but none confirmed routes to gear summary`() {
        val theDraft = draft(gearUses = listOf(gearUse("gear-use-1", confirmedUsedOnTrip = false)))
        assertEquals(WizardStep.GearSummary, nextWizardStepForDraft(theDraft))
        assertNull(nextGearUsePendingStatRectangle(theDraft))
    }

    @Test
    fun `confirmed gear missing a statistical sub-rectangle routes to gear stat rectangle`() {
        val pending = gearUse("gear-use-1", confirmedUsedOnTrip = true)
        val theDraft = draft(gearUses = listOf(pending))
        assertEquals(WizardStep.GearStatRectangle, nextWizardStepForDraft(theDraft))
        assertEquals(pending, nextGearUsePendingStatRectangle(theDraft))
    }

    @Test
    fun `first confirmed gear still pending is returned, not a later already-recorded one`() {
        val alreadyRecorded = gearUse("gear-use-1", confirmedUsedOnTrip = true, statisticalSubRectangleCode = "38E95")
        val stillPending = gearUse("gear-use-2", confirmedUsedOnTrip = true)
        val theDraft = draft(gearUses = listOf(alreadyRecorded, stillPending))
        assertEquals(stillPending, nextGearUsePendingStatRectangle(theDraft))
        assertEquals(WizardStep.GearStatRectangle, nextWizardStepForDraft(theDraft))
    }

    @Test
    fun `every confirmed gear has a recorded rectangle routes to landing storage`() {
        val gearOne = gearUse("gear-use-1", confirmedUsedOnTrip = true, statisticalSubRectangleCode = "38E95")
        val gearTwo = gearUse("gear-use-2", confirmedUsedOnTrip = true, statisticalSubRectangleCode = "38E98")
        val unconfirmed = gearUse("gear-use-3", confirmedUsedOnTrip = false)
        val theDraft = draft(gearUses = listOf(gearOne, gearTwo, unconfirmed))
        assertNull(nextGearUsePendingStatRectangle(theDraft))
        assertEquals(WizardStep.LandingStorage, nextWizardStepForDraft(theDraft))
    }

    @Test
    fun `earlier incomplete steps still take priority over the gear stat rectangle step`() {
        val pending = gearUse("gear-use-1", confirmedUsedOnTrip = true)
        val theDraft = draft(departurePort = null, gearUses = listOf(pending))
        assertEquals(WizardStep.DeparturePort, nextWizardStepForDraft(theDraft))
    }
}
