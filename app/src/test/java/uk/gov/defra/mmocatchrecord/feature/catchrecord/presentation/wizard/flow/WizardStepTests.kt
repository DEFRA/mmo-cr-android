package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.flow

import androidx.navigation.navOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uk.gov.defra.mmocatchrecord.common.navigation.CatchRecordGraphRoute
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DmyDate
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.NotLandedSpeciesEntry
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelection
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.PortSelectionMode
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.SpeciesWeightEntry
import java.time.Instant

class WizardStepTests {
    @Suppress("LongParameterList")
    private fun draft(
        isTripToday: Boolean? = true,
        departureDate: DmyDate? = null,
        returnDate: DmyDate? = null,
        departurePort: PortSelection? = PortSelection("port-1", PortSelectionMode.FirstTime),
        returnPort: PortSelection? = PortSelection("port-1", PortSelectionMode.FirstTime),
        gearUses: List<GearUse> = emptyList(),
        notLandedStraightAway: Boolean? = null,
        notLandedSpeciesEntries: List<NotLandedSpeciesEntry> = emptyList(),
    ) = CatchRecordDraft(
        id = "draft-1",
        vesselId = "vessel-1",
        isTripToday = isTripToday,
        departureDate = departureDate,
        returnDate = returnDate,
        departurePort = departurePort,
        returnPort = returnPort,
        gearUses = gearUses,
        notLandedStraightAway = notLandedStraightAway,
        notLandedSpeciesEntries = notLandedSpeciesEntries,
        modifiedAtEpochMillis = 0L,
    )

    private fun gearUse(
        id: String,
        confirmedUsedOnTrip: Boolean = false,
        statisticalSubRectangleCode: String? = null,
        speciesWeights: List<SpeciesWeightEntry> = emptyList(),
    ) = GearUse(
        id = id,
        gearTypeId = "gear-1",
        statisticalSubRectangleCode = statisticalSubRectangleCode,
        confirmedUsedOnTrip = confirmedUsedOnTrip,
        speciesWeights = speciesWeights,
    )

    /**
     * A confirmed-caught species entry — "this gear's species step is done", per
     * [SpeciesWeightEntry.confirmedCaught].
     */
    private fun confirmedSpecies(speciesId: String) =
        SpeciesWeightEntry(id = "sw-$speciesId", speciesId = speciesId, confirmedCaught = true)

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
        val alreadyRecorded =
            gearUse(
                "gear-use-1",
                confirmedUsedOnTrip = true,
                statisticalSubRectangleCode = "38E95",
                speciesWeights = listOf(confirmedSpecies("species-cod")),
            )
        val stillPending = gearUse("gear-use-2", confirmedUsedOnTrip = true)
        val theDraft = draft(gearUses = listOf(alreadyRecorded, stillPending))
        assertEquals(stillPending, nextGearUsePendingStatRectangle(theDraft))
        assertEquals(WizardStep.GearStatRectangle, nextWizardStepForDraft(theDraft))
    }

    @Test
    fun `a confirmed gear with a recorded rectangle but no species yet routes to gear species search`() {
        val gearOne = gearUse("gear-use-1", confirmedUsedOnTrip = true, statisticalSubRectangleCode = "38E95")
        val theDraft = draft(gearUses = listOf(gearOne))
        assertNull(nextGearUsePendingStatRectangle(theDraft))
        assertEquals(gearOne, nextGearUsePendingSpecies(theDraft))
        assertEquals(WizardStep.GearSpeciesSearch, nextWizardStepForDraft(theDraft))
    }

    @Test
    fun `a confirmed gear with an unconfirmed added species routes to gear species checklist, not search`() {
        val gearOne =
            gearUse(
                "gear-use-1",
                confirmedUsedOnTrip = true,
                statisticalSubRectangleCode = "38E95",
                speciesWeights = listOf(SpeciesWeightEntry(id = "sw-1", speciesId = "species-cod")),
            )
        val theDraft = draft(gearUses = listOf(gearOne))
        assertEquals(gearOne, nextGearUsePendingSpecies(theDraft))
        assertEquals(WizardStep.GearSpeciesChecklist, nextWizardStepForDraft(theDraft))
    }

    @Test
    fun `gear N's stat-rectangle and species steps both complete before gear N+1's stat-rectangle is reached`() {
        val gearOne = gearUse("gear-use-1", confirmedUsedOnTrip = true)
        val gearTwo = gearUse("gear-use-2", confirmedUsedOnTrip = true)
        val theDraft = draft(gearUses = listOf(gearOne, gearTwo))
        // Gear one's stat-rectangle step is reached first, even though gear two is also pending it.
        assertEquals(gearOne, nextGearUsePendingStatRectangle(theDraft))
        assertNull(nextGearUsePendingSpecies(theDraft))

        val gearOneWithRectangle = gearOne.copy(statisticalSubRectangleCode = "38E95")
        val afterRectangle = draft(gearUses = listOf(gearOneWithRectangle, gearTwo))
        // Gear one's own species step is now reached — NOT gear two's stat-rectangle step.
        assertNull(nextGearUsePendingStatRectangle(afterRectangle))
        assertEquals(gearOneWithRectangle, nextGearUsePendingSpecies(afterRectangle))
        assertEquals(WizardStep.GearSpeciesSearch, nextWizardStepForDraft(afterRectangle))

        val gearOneComplete = gearOneWithRectangle.copy(speciesWeights = listOf(confirmedSpecies("species-cod")))
        val afterGearOneComplete = draft(gearUses = listOf(gearOneComplete, gearTwo))
        // Only now does gear two's stat-rectangle step become the next step.
        assertEquals(gearTwo, nextGearUsePendingStatRectangle(afterGearOneComplete))
        assertEquals(WizardStep.GearStatRectangle, nextWizardStepForDraft(afterGearOneComplete))
    }

    @Test
    fun `every confirmed gear with a recorded rectangle and confirmed species routes to not-landed decision`() {
        val gearOne =
            gearUse(
                "gear-use-1",
                confirmedUsedOnTrip = true,
                statisticalSubRectangleCode = "38E95",
                speciesWeights = listOf(confirmedSpecies("species-cod")),
            )
        val gearTwo =
            gearUse(
                "gear-use-2",
                confirmedUsedOnTrip = true,
                statisticalSubRectangleCode = "38E98",
                speciesWeights = listOf(confirmedSpecies("species-plaice")),
            )
        val unconfirmed = gearUse("gear-use-3", confirmedUsedOnTrip = false)
        val theDraft = draft(gearUses = listOf(gearOne, gearTwo, unconfirmed))
        assertNull(nextGearUsePendingStatRectangle(theDraft))
        assertNull(nextGearUsePendingSpecies(theDraft))
        assertEquals(WizardStep.NotLandedStraightAwayDecision, nextWizardStepForDraft(theDraft))
    }

    @Test
    fun `not-landed decision answered false routes straight to check your answers`() {
        val gearOne =
            gearUse(
                "gear-use-1",
                confirmedUsedOnTrip = true,
                statisticalSubRectangleCode = "38E95",
                speciesWeights = listOf(confirmedSpecies("species-cod")),
            )
        val theDraft = draft(gearUses = listOf(gearOne), notLandedStraightAway = false)
        assertEquals(WizardStep.CheckYourAnswers, nextWizardStepForDraft(theDraft))
    }

    @Test
    fun `not-landed decision answered true with no species entries yet routes to not-landed species`() {
        val gearOne =
            gearUse(
                "gear-use-1",
                confirmedUsedOnTrip = true,
                statisticalSubRectangleCode = "38E95",
                speciesWeights = listOf(confirmedSpecies("species-cod")),
            )
        val theDraft = draft(gearUses = listOf(gearOne), notLandedStraightAway = true)
        assertEquals(WizardStep.NotLandedStraightAwaySpecies, nextWizardStepForDraft(theDraft))
    }

    @Test
    fun `not-landed decision answered true with species entries recorded routes to check your answers`() {
        val gearOne =
            gearUse(
                "gear-use-1",
                confirmedUsedOnTrip = true,
                statisticalSubRectangleCode = "38E95",
                speciesWeights = listOf(confirmedSpecies("species-cod")),
            )
        val theDraft =
            draft(
                gearUses = listOf(gearOne),
                notLandedStraightAway = true,
                notLandedSpeciesEntries = listOf(NotLandedSpeciesEntry(speciesId = "species-cod")),
            )
        assertEquals(WizardStep.CheckYourAnswers, nextWizardStepForDraft(theDraft))
    }

    @Test
    fun `earlier incomplete steps still take priority over the gear stat rectangle step`() {
        val pending = gearUse("gear-use-1", confirmedUsedOnTrip = true)
        val theDraft = draft(departurePort = null, gearUses = listOf(pending))
        assertEquals(WizardStep.DeparturePort, nextWizardStepForDraft(theDraft))
    }

    @Test
    fun `submitted draft routes to submission success regardless of late-submission timing`() {
        val theDraft = draft(returnDate = DmyDate(1, 1, 2020)).copy(status = DraftStatus.Submitted)
        val farFuture = Instant.parse("2020-06-01T00:00:00Z").toEpochMilli()
        assertEquals(WizardStep.SubmissionSuccess, resolveSubmissionStep(theDraft, farFuture))
    }

    @Test
    fun `pending-sync draft routes to submission pending sync`() {
        val theDraft = draft().copy(status = DraftStatus.PendingSync)
        assertEquals(WizardStep.SubmissionPendingSync, resolveSubmissionStep(theDraft))
    }

    @Test
    fun `late unacknowledged submission routes to the late-submission warning`() {
        val returnDate = DmyDate(1, 1, 2020)
        val theDraft = draft(returnDate = returnDate)
        // Well over 24h after 2020-01-01 23:59 UTC.
        val muchLater = Instant.parse("2020-01-05T00:00:00Z").toEpochMilli()
        assertEquals(WizardStep.LateSubmissionWarning, resolveSubmissionStep(theDraft, muchLater))
    }

    @Test
    fun `late submission already acknowledged skips the warning and routes to check your answers`() {
        val returnDate = DmyDate(1, 1, 2020)
        val theDraft = draft(returnDate = returnDate).copy(lateSubmissionWarningAcknowledged = true)
        val muchLater = Instant.parse("2020-01-05T00:00:00Z").toEpochMilli()
        assertEquals(WizardStep.CheckYourAnswers, resolveSubmissionStep(theDraft, muchLater))
    }

    @Test
    fun `submission within 24 hours routes straight to check your answers, no warning`() {
        val returnDate = DmyDate(1, 1, 2020)
        val theDraft = draft(returnDate = returnDate)
        val soonAfter = Instant.parse("2020-01-02T00:00:00Z").toEpochMilli()
        assertEquals(WizardStep.CheckYourAnswers, resolveSubmissionStep(theDraft, soonAfter))
    }

    // --- applySubmissionResultNavOptions ------------------------------------------------------------
    //
    // Regression coverage for the real navigation bug found and fixed while verifying Phase 8's
    // "Accept and submit trip details" wiring: transitioning into a submission-result step must clear the
    // wizard's back stack down to (but not including, i.e. not popping) the nav graph's own entry, so the
    // nav-graph-scoped `CatchRecordFlowViewModel` instance survives, while every other step is a no-op.

    @Test
    fun `applySubmissionResultNavOptions pops up to the graph route, non-inclusive, for submission success`() {
        val options = navOptions { applySubmissionResultNavOptions(WizardStep.SubmissionSuccess) }
        assertEquals(CatchRecordGraphRoute::class, options.popUpToRouteClass)
        assertEquals(false, options.isPopUpToInclusive())
    }

    @Test
    fun `applySubmissionResultNavOptions pops up to the graph route, non-inclusive, for submission pending sync`() {
        val options = navOptions { applySubmissionResultNavOptions(WizardStep.SubmissionPendingSync) }
        assertEquals(CatchRecordGraphRoute::class, options.popUpToRouteClass)
        assertEquals(false, options.isPopUpToInclusive())
    }

    @Test
    fun `applySubmissionResultNavOptions is a no-op for every other wizard step`() {
        val options = navOptions { applySubmissionResultNavOptions(WizardStep.CheckYourAnswers) }
        assertNull(options.popUpToRoute)
    }
}
