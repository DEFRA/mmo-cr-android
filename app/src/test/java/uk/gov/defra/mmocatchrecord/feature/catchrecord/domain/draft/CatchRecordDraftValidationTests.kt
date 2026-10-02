package uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JUnit (no Android/Robolectric dependency) tests for [CatchRecordDraftValidation] — the submission
 * boundary gate that must never let an incomplete draft be submitted (see that object's doc comment for
 * why it exists independently of the wizard's own step-by-step gating).
 */
class CatchRecordDraftValidationTests {
    private fun completeDraft(
        status: DraftStatus = DraftStatus.ReadyToSubmit,
        gearUses: List<GearUse> = listOf(confirmedCompleteGearUse()),
        notLandedStraightAway: Boolean? = false,
        notLandedSpeciesEntries: List<NotLandedSpeciesEntry> = emptyList(),
    ) = CatchRecordDraft(
        id = "draft-1",
        vesselId = "vessel-1",
        isTripToday = true,
        departureDate = DmyDate(1, 1, 2026),
        returnDate = DmyDate(2, 1, 2026),
        departurePort = PortSelection("port-1", PortSelectionMode.FirstTime),
        returnPort = PortSelection("port-1", PortSelectionMode.FirstTime),
        gearUses = gearUses,
        notLandedStraightAway = notLandedStraightAway,
        notLandedSpeciesEntries = notLandedSpeciesEntries,
        status = status,
        modifiedAtEpochMillis = 0L,
    )

    private fun confirmedCompleteGearUse(
        statisticalSubRectangleCode: String? = "38E95",
        speciesWeights: List<SpeciesWeightEntry> = listOf(SpeciesWeightEntry(id = "sw-1", speciesId = "cod", confirmedCaught = true)),
    ) = GearUse(
        id = "gear-use-1",
        gearTypeId = "gear-seine-nets",
        statisticalSubRectangleCode = statisticalSubRectangleCode,
        speciesWeights = speciesWeights,
        confirmedUsedOnTrip = true,
    )

    private fun issuesOf(draft: CatchRecordDraft): List<DraftSubmissionIssue> {
        val result = CatchRecordDraftValidation.validateForSubmission(draft)
        assertTrue("expected Invalid but was $result", result is DraftSubmissionValidation.Invalid)
        return (result as DraftSubmissionValidation.Invalid).issues
    }

    @Test
    fun aFullyCompleteDraftIsValid() {
        val result = CatchRecordDraftValidation.validateForSubmission(completeDraft())

        assertEquals(DraftSubmissionValidation.Valid, result)
    }

    @Test
    fun aDraftAlreadySubmittedIsRejectedAsNotInSubmittableStatus() {
        val issues = issuesOf(completeDraft(status = DraftStatus.Submitted))

        assertTrue(issues.contains(DraftSubmissionIssue.DraftNotInSubmittableStatus))
    }

    @Test
    fun aDiscardedDraftIsRejectedAsNotInSubmittableStatus() {
        val issues = issuesOf(completeDraft(status = DraftStatus.Discarded))

        assertTrue(issues.contains(DraftSubmissionIssue.DraftNotInSubmittableStatus))
    }

    @Test
    fun aPendingSyncDraftIsStillSubmittable() {
        // CatchRecordSyncWorker's background retry re-validates a PendingSync draft before resubmitting it
        // — it must not be rejected purely for being PendingSync (only Submitted/Discarded are terminal).
        val result = CatchRecordDraftValidation.validateForSubmission(completeDraft(status = DraftStatus.PendingSync))

        assertEquals(DraftSubmissionValidation.Valid, result)
    }

    @Test
    fun missingTripTimingIsReported() {
        val draft = completeDraft().copy(isTripToday = null)

        assertTrue(issuesOf(draft).contains(DraftSubmissionIssue.TripTimingIncomplete))
    }

    @Test
    fun missingPortsIsReported() {
        val draft = completeDraft().copy(departurePort = null)

        assertTrue(issuesOf(draft).contains(DraftSubmissionIssue.PortsIncomplete))
    }

    @Test
    fun noConfirmedGearUseShortCircuitsWithOnlyThatSingleIssue() {
        val draft = completeDraft(gearUses = listOf(confirmedCompleteGearUse().copy(confirmedUsedOnTrip = false)))

        // gearAndLandingIssues returns early with just NoConfirmedGear — the subsequent gear/landing checks
        // (sub-rectangle, confirmed species) are skipped entirely once that early return fires.
        val issues = issuesOf(draft)
        assertEquals(listOf(DraftSubmissionIssue.NoConfirmedGear), issues)
    }

    @Test
    fun aConfirmedGearUseMissingAStatisticalSubRectangleIsReported() {
        val draft = completeDraft(gearUses = listOf(confirmedCompleteGearUse(statisticalSubRectangleCode = null)))

        assertTrue(issuesOf(draft).contains(DraftSubmissionIssue.GearMissingStatisticalSubRectangle))
    }

    @Test
    fun aConfirmedGearUseWithNoConfirmedSpeciesIsReported() {
        val draft =
            completeDraft(
                gearUses =
                    listOf(
                        confirmedCompleteGearUse(
                            speciesWeights = listOf(SpeciesWeightEntry(id = "sw-1", speciesId = "cod", confirmedCaught = false)),
                        ),
                    ),
            )

        assertTrue(issuesOf(draft).contains(DraftSubmissionIssue.GearMissingConfirmedSpecies))
    }

    @Test
    fun notLandedDecisionUnansweredIsReported() {
        val draft = completeDraft(notLandedStraightAway = null)

        assertTrue(issuesOf(draft).contains(DraftSubmissionIssue.NotLandedDecisionUnanswered))
    }

    @Test
    fun notLandedStraightAwayTrueWithNoSpeciesEntriesIsReported() {
        val draft = completeDraft(notLandedStraightAway = true, notLandedSpeciesEntries = emptyList())

        assertTrue(issuesOf(draft).contains(DraftSubmissionIssue.NotLandedSpeciesIncomplete))
    }

    @Test
    fun notLandedStraightAwayTrueWithSpeciesEntriesIsNotReported() {
        val draft =
            completeDraft(
                notLandedStraightAway = true,
                notLandedSpeciesEntries = listOf(NotLandedSpeciesEntry(speciesId = "cod", weightAboveMinimumSizeKeptOnboardKg = 1.0)),
            )

        val result = CatchRecordDraftValidation.validateForSubmission(draft)

        assertEquals(DraftSubmissionValidation.Valid, result)
    }

    @Test
    fun multipleSimultaneousIssuesAreAllReportedTogether() {
        val draft =
            completeDraft(status = DraftStatus.Submitted, notLandedStraightAway = null)
                .copy(isTripToday = null, departurePort = null)

        val issues = issuesOf(draft)

        assertEquals(
            setOf(
                DraftSubmissionIssue.DraftNotInSubmittableStatus,
                DraftSubmissionIssue.TripTimingIncomplete,
                DraftSubmissionIssue.PortsIncomplete,
                DraftSubmissionIssue.NotLandedDecisionUnanswered,
            ),
            issues.toSet(),
        )
    }
}
