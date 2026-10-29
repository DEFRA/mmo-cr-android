package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import uk.gov.defra.mmocatchrecord.R
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.SpeciesWeightEntry
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Species
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.SpeciesWeightPrecision
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear.GearSpeciesChecklistScreenTestTags as Tags

// Acceptance tests for the species catch weight story (AC1–AC11); error copy is asserted verbatim from the ticket.
class SpeciesCatchWeightTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val cod =
        Species(
            id = "species-cod",
            name = "Atlantic cod (COD)",
            faoCode = "COD",
            weightPrecision = SpeciesWeightPrecision.OneDecimalPlace,
            weightAboveMinimumSizeMandatory = true,
        )

    private val plaice =
        Species(
            id = "species-plaice",
            name = "Plaice (PLE)",
            faoCode = "PLE",
            weightPrecision = SpeciesWeightPrecision.WholeNumber,
            weightAboveMinimumSizeMandatory = true,
        )

    private val speciesList = listOf(cod, plaice)
    private var submitted: CatchRecordDraft? = null
    private var removed: CatchRecordDraft? = null
    private var addSpeciesClicks = 0

    @Test
    fun ac1_tickingSpeciesRevealsAboveMinimumFieldAndWeightCanBeEntered() {
        showChecklist(cod)

        composeTestRule.onNodeWithTag(aboveMin(cod)).assertDoesNotExist()
        tick(0)
        composeTestRule.onNodeWithTag(aboveMin(cod)).assertIsDisplayed()
        enter(aboveMin(cod), "25")
        saveAndContinue()

        assertEquals(25.0, savedEntry(cod)?.weightAboveMinimumSizeKg)
        assertEquals(true, savedEntry(cod)?.confirmedCaught)
    }

    @Test
    fun ac2_addBelowMinimumRevealsFieldAndWeightIsSaved() {
        showChecklist(cod)
        tick(0)

        composeTestRule.onNodeWithTag(belowMin(cod)).assertDoesNotExist()
        composeTestRule.onNodeWithTag("${Tags.ADD_BELOW_MIN_ACTION_PREFIX}_${cod.id}").performClick()
        composeTestRule.onNodeWithTag(belowMin(cod)).assertIsDisplayed()
        enter(aboveMin(cod), "20")
        enter(belowMin(cod), "3")
        saveAndContinue()

        assertEquals(3.0, savedEntry(cod)?.weightBelowMinimumSizeKg)
    }

    @Test
    fun ac2_addLegallyDiscardedRevealsFieldAndWeightIsSaved() {
        showChecklist(cod)
        tick(0)

        composeTestRule.onNodeWithTag(discarded(cod)).assertDoesNotExist()
        composeTestRule.onNodeWithTag("${Tags.ADD_DISCARDED_ACTION_PREFIX}_${cod.id}").performClick()
        composeTestRule.onNodeWithTag(discarded(cod)).assertIsDisplayed()
        enter(aboveMin(cod), "20")
        enter(discarded(cod), "2")
        saveAndContinue()

        assertEquals(2.0, savedEntry(cod)?.weightLegallyDiscardedKg)
    }

    @Test
    fun ac2_optionalFieldsCanBeLeftBlank() {
        showChecklist(cod)
        tick(0)
        revealOptionalFields(cod)

        enter(aboveMin(cod), "20")
        saveAndContinue()

        assertEquals(20.0, savedEntry(cod)?.weightAboveMinimumSizeKg)
        assertNull(savedEntry(cod)?.weightBelowMinimumSizeKg)
        assertNull(savedEntry(cod)?.weightLegallyDiscardedKg)
    }

    @Test
    fun ac3_allThreeWeightsCanBeEditedBeforeSaving() {
        showChecklist(cod)
        tick(0)
        revealOptionalFields(cod)
        enter(aboveMin(cod), "10")
        enter(belowMin(cod), "4")
        enter(discarded(cod), "3")

        replace(aboveMin(cod), "12")
        replace(belowMin(cod), "5")
        replace(discarded(cod), "1")
        saveAndContinue()

        assertEquals(12.0, savedEntry(cod)?.weightAboveMinimumSizeKg)
        assertEquals(5.0, savedEntry(cod)?.weightBelowMinimumSizeKg)
        assertEquals(1.0, savedEntry(cod)?.weightLegallyDiscardedKg)
    }

    @Test
    fun ac4_addSpeciesReturnsToAddSpeciesPage() {
        showChecklist(cod)

        composeTestRule.onNodeWithTag(Tags.ADD_SPECIES_ACTION).assertIsDisplayed().performClick()

        assertEquals(1, addSpeciesClicks)
        assertNull(submitted)
    }

    @Test
    fun ac5_removeSpeciesDirectsToRemoveSpeciesPageWithoutRemovingImmediately() {
        showChecklist(cod, plaice)
        tick(0)

        composeTestRule.onNodeWithTag(Tags.REMOVE_SPECIES_ACTION).assertIsDisplayed().performClick()

        // Ticket: removal happens on the separate "remove species from vessel" page, not on this screen.
        assertTrue("Remove species trigger was not invoked", removed != null)
        val remainingSpeciesIds =
            removed
                ?.gearUses
                ?.single()
                ?.speciesWeights
                ?.map { it.speciesId }
        assertEquals(listOf(cod.id, plaice.id), remainingSpeciesIds)
    }

    @Test
    fun ac6_blankAboveMinimumWeightBlocksSaveAndContinue() {
        showChecklist(cod)
        tick(0)

        saveAndContinue()

        assertBlockedWithError(AC6_REQUIRED)
    }

    @Test
    fun ac7_discardedHigherThanRetainedBlocksSaveAndContinue() {
        showChecklist(cod)
        tick(0)
        revealOptionalFields(cod)
        enter(aboveMin(cod), "5")
        enter(discarded(cod), "10")

        saveAndContinue()

        assertBlockedWithError(AC7_DISCARDED_NOT_LOWER)
    }

    @Test
    fun ac7_discardedSameAsRetainedBlocksSaveAndContinue() {
        showChecklist(cod)
        tick(0)
        revealOptionalFields(cod)
        enter(aboveMin(cod), "5")
        enter(discarded(cod), "5")

        saveAndContinue()

        assertBlockedWithError(AC7_DISCARDED_NOT_LOWER)
    }

    @Test
    fun ac8_wholeNumberAndOneDecimalPlaceWeightsAreAccepted() {
        showChecklist(cod)
        tick(0)
        revealOptionalFields(cod)
        enter(aboveMin(cod), "10")
        enter(belowMin(cod), "10.2")

        saveAndContinue()

        assertEquals(10.0, savedEntry(cod)?.weightAboveMinimumSizeKg)
        assertEquals(10.2, savedEntry(cod)?.weightBelowMinimumSizeKg)
    }

    @Test
    fun ac8_weightWithTwoDecimalPlacesBlocksSaveAndContinue() {
        showChecklist(cod)
        tick(0)
        enter(aboveMin(cod), "10.55")

        saveAndContinue()

        assertBlockedWithError("The weight for the Atlantic cod must be a number with up to one decimal place.")
    }

    @Test
    fun ac8_decimalWeightForWholeNumberSpeciesBlocksSaveAndContinue() {
        showChecklist(plaice)
        tick(0)
        enter(aboveMin(plaice), "10.5")

        saveAndContinue()

        assertBlockedWithError("the weight of the Plaice must be a whole number")
    }

    @Test
    fun scenario9_zeroInAllThreeFieldsBlocksSaveAndContinue() {
        showChecklist(cod)
        tick(0)
        revealOptionalFields(cod)
        enter(aboveMin(cod), "0")
        enter(belowMin(cod), "0")
        enter(discarded(cod), "0")

        saveAndContinue()

        assertBlockedWithError("The weight for the Atlantic cod must be a more the 0KG")
    }

    @Test
    fun scenario10_weightOfExactly10000IsAccepted() {
        showChecklist(cod)
        tick(0)
        enter(aboveMin(cod), "10000")

        saveAndContinue()

        assertEquals(10_000.0, savedEntry(cod)?.weightAboveMinimumSizeKg)
    }

    @Test
    fun scenario10_over10000InAllThreeFieldsBlocksSaveAndContinue() {
        showChecklist(cod)
        tick(0)
        revealOptionalFields(cod)
        enter(aboveMin(cod), "10001")
        enter(belowMin(cod), "10001")
        enter(discarded(cod), "10001")

        saveAndContinue()

        assertBlockedWithError("The weight for the Atlantic cod must be a 10,000 or less")
    }

    @Test
    fun scenario11_negativeWeightBlocksSaveAndContinue() {
        showChecklist(cod)
        tick(0)
        enter(aboveMin(cod), "-8")

        saveAndContinue()

        assertBlockedWithError("The weight for Atlantic cod must be more than 0kg")
    }

    @Test
    fun scenario11_weightWithLettersBlocksSaveAndContinue() {
        showChecklist(cod)
        tick(0)
        enter(aboveMin(cod), "A1")

        saveAndContinue()

        assertBlockedWithError("The weight for Atlantic cod must be a number")
    }

    @Test
    fun selectedSpeciesFromPreviousPageAreListedWithoutWeightFields() {
        showChecklist(cod, plaice)

        composeTestRule.onNodeWithText(cod.name).assertIsDisplayed()
        composeTestRule.onNodeWithText(plaice.name).assertIsDisplayed()
        composeTestRule.onNodeWithTag(aboveMin(cod)).assertDoesNotExist()
        composeTestRule.onNodeWithTag(aboveMin(plaice)).assertDoesNotExist()
    }

    @Test
    fun noSpeciesTickedBlocksSaveAndContinue() {
        showChecklist(cod)

        saveAndContinue()

        assertNull("User must not be able to continue", submitted)
        composeTestRule
            .onNodeWithTag(Tags.CHECKLIST_ERROR)
            .assertIsDisplayed()
            .assertTextEquals(string(R.string.species_checklist_error_required))
    }

    @Test
    fun untickingASpeciesHidesItsFieldsAndExcludesItFromValidationAndSave() {
        showChecklist(cod, plaice)
        tick(0)
        tick(1)
        enter(aboveMin(cod), "0")
        enter(aboveMin(plaice), "7")

        tick(0)
        composeTestRule.onNodeWithTag(aboveMin(cod)).assertDoesNotExist()
        saveAndContinue()

        assertNotNull("Unticked species must not block Save and Continue", submitted)
        assertEquals(false, savedEntry(cod)?.confirmedCaught)
        assertNull(savedEntry(cod)?.weightAboveMinimumSizeKg)
        assertEquals(7.0, savedEntry(plaice)?.weightAboveMinimumSizeKg)
    }

    @Test
    fun weightsForEachSelectedSpeciesAreSavedAgainstTheirOwnRecord() {
        showChecklist(cod, plaice)
        tick(0)
        tick(1)
        enter(aboveMin(cod), "12.5")
        enter(aboveMin(plaice), "30")

        saveAndContinue()

        assertEquals(12.5, savedEntry(cod)?.weightAboveMinimumSizeKg)
        assertEquals(30.0, savedEntry(plaice)?.weightAboveMinimumSizeKg)
        assertEquals(true, savedEntry(cod)?.confirmedCaught)
        assertEquals(true, savedEntry(plaice)?.confirmedCaught)
    }

    @Test
    fun eachSpeciesIsValidatedSeparatelyAndTheErrorNamesTheFailingSpecies() {
        showChecklist(cod, plaice)
        tick(0)
        tick(1)
        enter(aboveMin(cod), "20")
        enter(aboveMin(plaice), "10.5")

        saveAndContinue()

        assertNull("User must not be able to continue", submitted)
        composeTestRule
            .onAllNodesWithText(string(R.string.species_weight_error_whole_number, "Plaice"))
            .onFirst()
            .assertExists()
        composeTestRule.onAllNodesWithText("Weight for Atlantic cod", substring = true).assertCountEquals(0)
    }

    @Test
    fun ac3_correctingAnInvalidWeightAllowsSaveAndContinue() {
        showChecklist(cod)
        tick(0)
        enter(aboveMin(cod), "0")
        saveAndContinue()
        assertNull("User must not be able to continue", submitted)

        replace(aboveMin(cod), "15")
        saveAndContinue()

        assertEquals(15.0, savedEntry(cod)?.weightAboveMinimumSizeKg)
    }

    @Test
    fun ac3_previouslySavedWeightsAreShownAndCanBeAmended() {
        showChecklistWith(
            SpeciesWeightEntry(
                id = "sw-0",
                speciesId = cod.id,
                confirmedCaught = true,
                weightAboveMinimumSizeKg = 20.0,
                weightBelowMinimumSizeKg = 4.0,
                weightLegallyDiscardedKg = 2.0,
            ),
        )

        field(aboveMin(cod)).assertTextEquals("20")
        replace(aboveMin(cod), "25")
        replace(belowMin(cod), "6")
        replace(discarded(cod), "3")
        saveAndContinue()

        assertEquals(25.0, savedEntry(cod)?.weightAboveMinimumSizeKg)
        assertEquals(6.0, savedEntry(cod)?.weightBelowMinimumSizeKg)
        assertEquals(3.0, savedEntry(cod)?.weightLegallyDiscardedKg)
    }

    @Test
    fun ac7_discardedLowerThanRetainedIsAccepted() {
        showChecklist(cod)
        tick(0)
        revealOptionalFields(cod)
        enter(aboveMin(cod), "10")
        enter(discarded(cod), "9.9")

        saveAndContinue()

        assertEquals(10.0, savedEntry(cod)?.weightAboveMinimumSizeKg)
        assertEquals(9.9, savedEntry(cod)?.weightLegallyDiscardedKg)
    }

    @Test
    fun scenario9_zeroInAllThreeFieldsShowsAnErrorForEveryField() {
        showChecklist(cod)
        tick(0)
        revealOptionalFields(cod)
        enter(aboveMin(cod), "0")
        enter(belowMin(cod), "0")
        enter(discarded(cod), "0")

        saveAndContinue()

        assertNull("User must not be able to continue", submitted)
        // Three fields, each listed in the error summary and shown inline.
        composeTestRule
            .onAllNodesWithText(string(R.string.species_weight_error_below_minimum, "Atlantic cod"))
            .assertCountEquals(6)
    }

    @Test
    fun scenario9_smallestPositiveWeightIsAccepted() {
        showChecklist(cod)
        tick(0)
        enter(aboveMin(cod), "0.1")

        saveAndContinue()

        assertEquals(0.1, savedEntry(cod)?.weightAboveMinimumSizeKg)
    }

    @Test
    fun scenario10_weightJustBelowTheMaximumIsAccepted() {
        showChecklist(cod)
        tick(0)
        enter(aboveMin(cod), "9999.9")

        saveAndContinue()

        assertEquals(9_999.9, savedEntry(cod)?.weightAboveMinimumSizeKg)
    }

    @Test
    fun scenario10_weightJustAboveTheMaximumBlocksSaveAndContinue() {
        showChecklist(cod)
        tick(0)
        enter(aboveMin(cod), "10000.1")

        saveAndContinue()

        assertNull("User must not be able to continue", submitted)
        composeTestRule
            .onAllNodesWithText(string(R.string.species_weight_error_above_maximum, "Atlantic cod"))
            .onFirst()
            .assertExists()
    }

    @Test
    fun optionalBelowMinimumWeightMustBeValidWhenEntered() {
        showChecklist(cod)
        tick(0)
        revealOptionalFields(cod)
        enter(aboveMin(cod), "10")
        enter(belowMin(cod), "10001")

        saveAndContinue()

        assertNull("User must not be able to continue", submitted)
        composeTestRule
            .onAllNodesWithText(string(R.string.species_weight_error_above_maximum, "Atlantic cod"))
            .onFirst()
            .assertExists()
    }

    private fun string(
        resId: Int,
        vararg args: Any,
    ): String = ApplicationProvider.getApplicationContext<Context>().getString(resId, *args)

    private fun showChecklist(vararg species: Species) {
        showChecklistWith(
            *species
                .mapIndexed { index, s ->
                    SpeciesWeightEntry(id = "sw-$index", speciesId = s.id, confirmedCaught = false)
                }.toTypedArray(),
        )
    }

    private fun showChecklistWith(vararg entries: SpeciesWeightEntry) {
        val gearUse =
            GearUse(
                id = "gear-use-1",
                gearTypeId = "gear-seine-nets",
                statisticalSubRectangleCode = "38E95",
                speciesWeights = entries.toList(),
                confirmedUsedOnTrip = true,
            )
        val draft =
            CatchRecordDraft(
                id = "draft-1",
                vesselId = "vessel-1",
                gearUses = listOf(gearUse),
                modifiedAtEpochMillis = 0L,
                status = DraftStatus.Draft,
            )
        composeTestRule.setContent {
            MmoTheme {
                GearSpeciesChecklistScreenContent(
                    draft = draft,
                    gearUse = gearUse,
                    speciesList = speciesList,
                    onRemoveSpecies = { removed = it },
                    onAddAnotherSpecies = { addSpeciesClicks++ },
                    onSubmit = { submitted = it },
                )
            }
        }
    }

    private fun tick(index: Int) {
        composeTestRule.onNodeWithTag("${Tags.CHECKBOX_PREFIX}_$index").performClick()
    }

    private fun revealOptionalFields(species: Species) {
        composeTestRule.onNodeWithTag("${Tags.ADD_BELOW_MIN_ACTION_PREFIX}_${species.id}").performClick()
        composeTestRule.onNodeWithTag("${Tags.ADD_DISCARDED_ACTION_PREFIX}_${species.id}").performClick()
    }

    private fun field(tag: String): SemanticsNodeInteraction = composeTestRule.onNodeWithTag(tag)

    private fun enter(
        tag: String,
        text: String,
    ) {
        field(tag).performTextInput(text)
    }

    private fun replace(
        tag: String,
        text: String,
    ) {
        field(tag).performTextClearance()
        field(tag).performTextInput(text)
    }

    private fun saveAndContinue() {
        // Semantics click so the soft keyboard or an input hint can't push the button off-screen.
        composeTestRule.onNodeWithTag(Tags.SAVE_ACTION).performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()
    }

    private fun assertBlockedWithError(message: String) {
        assertNull("User must not be able to continue", submitted)
        composeTestRule.onNodeWithTag(Tags.ERROR_SUMMARY).assertIsDisplayed()
        composeTestRule.onAllNodesWithText(message).onFirst().assertIsDisplayed()
    }

    private fun savedEntry(species: Species): SpeciesWeightEntry? =
        submitted
            ?.gearUses
            ?.single()
            ?.speciesWeights
            ?.single { it.speciesId == species.id }

    private fun aboveMin(species: Species) = "${Tags.ABOVE_MIN_FIELD_PREFIX}_${species.id}"

    private fun belowMin(species: Species) = "${Tags.BELOW_MIN_FIELD_PREFIX}_${species.id}"

    private fun discarded(species: Species) = "${Tags.DISCARDED_FIELD_PREFIX}_${species.id}"

    private companion object {
        const val AC6_REQUIRED = "Enter the Weight above minimum  minimum size retained"
        const val AC7_DISCARDED_NOT_LOWER =
            "the Weight above minimum  minimum size retained must be higher then Weight legally discarded"
    }
}
