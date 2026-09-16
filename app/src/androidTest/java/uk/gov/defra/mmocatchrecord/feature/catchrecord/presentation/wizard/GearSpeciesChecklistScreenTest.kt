package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.SpeciesWeightEntry
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Species
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.SpeciesWeightPrecision

/**
 * Exercises [GearSpeciesChecklistScreenContent] directly (the public, state-independent content
 * composable) rather than the private state-driven wrapper — mirrors the same approach other wizard
 * screen tests take when the outer `internal`/`private` wrapper only adds scaffold/title chrome.
 */
class GearSpeciesChecklistScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val seineNets = GearType(id = "gear-seine-nets", name = "Seine nets (not specified)")

    // Mandatory above-min field, one decimal place allowed, no quota configured.
    private val cod =
        Species(
            id = "species-cod",
            name = "Atlantic cod (COD)",
            faoCode = "COD",
            weightPrecision = SpeciesWeightPrecision.OneDecimalPlace,
            weightAboveMinimumSizeMandatory = true,
        )

    // Optional above-min field, whole numbers only, small monthly quota so the quota path is exercisable.
    private val plaice =
        Species(
            id = "species-plaice",
            name = "Plaice (PLE)",
            faoCode = "PLE",
            weightPrecision = SpeciesWeightPrecision.WholeNumber,
            weightAboveMinimumSizeMandatory = false,
            monthlyQuotaKg = 10.0,
        )

    private val speciesList = listOf(cod, plaice)

    private fun gearUseWithAddedSpecies(speciesIds: List<String>) =
        GearUse(
            id = "gear-use-1",
            gearTypeId = seineNets.id,
            statisticalSubRectangleCode = "38E95",
            speciesWeights =
                speciesIds.mapIndexed { index, id ->
                    SpeciesWeightEntry(id = "sw-$index", speciesId = id, confirmedCaught = false)
                },
            confirmedUsedOnTrip = true,
        )

    private fun draftWith(gearUse: GearUse) =
        CatchRecordDraft(
            id = "draft-1",
            vesselId = "vessel-1",
            gearUses = listOf(gearUse),
            modifiedAtEpochMillis = 0L,
            status = DraftStatus.Draft,
        )

    @Test
    fun checkingASpeciesRevealsTheAboveMinimumFieldAndSubmittingSavesIt() {
        var submitted: CatchRecordDraft? = null
        val gearUse = gearUseWithAddedSpecies(listOf(cod.id))
        composeTestRule.setContent {
            MmoTheme {
                GearSpeciesChecklistScreenContent(
                    draft = draftWith(gearUse),
                    gearUse = gearUse,
                    speciesList = speciesList,
                    onRemoveSpecies = {},
                    onAddAnotherSpecies = {},
                    onSubmit = { submitted = it },
                )
            }
        }

        composeTestRule.onNodeWithTag("${GearSpeciesChecklistScreenTestTags.CHECKBOX_PREFIX}_0").performClick()
        composeTestRule
            .onNodeWithTag("${GearSpeciesChecklistScreenTestTags.ABOVE_MIN_FIELD_PREFIX}_${cod.id}")
            .performTextInput("5.5")
        composeTestRule.onNodeWithTag(GearSpeciesChecklistScreenTestTags.SAVE_ACTION).performClick()

        val savedEntry =
            submitted
                ?.gearUses
                ?.single()
                ?.speciesWeights
                ?.single { it.speciesId == cod.id }
        assertEquals(true, savedEntry?.confirmedCaught)
        assertEquals(5.5, savedEntry?.weightAboveMinimumSizeKg)
    }

    @Test
    fun addBelowMinimumLinkRevealsFieldAndRemoveLinkClearsItAndCollapsesItAgain() {
        val gearUse = gearUseWithAddedSpecies(listOf(cod.id))
        composeTestRule.setContent {
            MmoTheme {
                GearSpeciesChecklistScreenContent(
                    draft = draftWith(gearUse),
                    gearUse = gearUse,
                    speciesList = speciesList,
                    onRemoveSpecies = {},
                    onAddAnotherSpecies = {},
                    onSubmit = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("${GearSpeciesChecklistScreenTestTags.CHECKBOX_PREFIX}_0").performClick()
        composeTestRule
            .onNodeWithTag("${GearSpeciesChecklistScreenTestTags.ADD_BELOW_MIN_ACTION_PREFIX}_${cod.id}")
            .performClick()
        composeTestRule
            .onNodeWithTag("${GearSpeciesChecklistScreenTestTags.BELOW_MIN_FIELD_PREFIX}_${cod.id}")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("${GearSpeciesChecklistScreenTestTags.BELOW_MIN_FIELD_PREFIX}_${cod.id}")
            .performTextInput("2")
        composeTestRule
            .onNodeWithTag("${GearSpeciesChecklistScreenTestTags.REMOVE_BELOW_MIN_ACTION_PREFIX}_${cod.id}")
            .performClick()

        composeTestRule
            .onNodeWithTag("${GearSpeciesChecklistScreenTestTags.ADD_BELOW_MIN_ACTION_PREFIX}_${cod.id}")
            .assertIsDisplayed()
    }

    @Test
    fun addDiscardedLinkRevealsFieldAndCanBeSubmittedAlongsideAboveMinimum() {
        var submitted: CatchRecordDraft? = null
        val gearUse = gearUseWithAddedSpecies(listOf(cod.id))
        composeTestRule.setContent {
            MmoTheme {
                GearSpeciesChecklistScreenContent(
                    draft = draftWith(gearUse),
                    gearUse = gearUse,
                    speciesList = speciesList,
                    onRemoveSpecies = {},
                    onAddAnotherSpecies = {},
                    onSubmit = { submitted = it },
                )
            }
        }

        composeTestRule.onNodeWithTag("${GearSpeciesChecklistScreenTestTags.CHECKBOX_PREFIX}_0").performClick()
        composeTestRule
            .onNodeWithTag("${GearSpeciesChecklistScreenTestTags.ABOVE_MIN_FIELD_PREFIX}_${cod.id}")
            .performTextInput("5")
        composeTestRule
            .onNodeWithTag("${GearSpeciesChecklistScreenTestTags.ADD_DISCARDED_ACTION_PREFIX}_${cod.id}")
            .performClick()
        composeTestRule
            .onNodeWithTag("${GearSpeciesChecklistScreenTestTags.DISCARDED_FIELD_PREFIX}_${cod.id}")
            .performTextInput("1")
        composeTestRule.onNodeWithTag(GearSpeciesChecklistScreenTestTags.SAVE_ACTION).performClick()

        val savedEntry =
            submitted
                ?.gearUses
                ?.single()
                ?.speciesWeights
                ?.single { it.speciesId == cod.id }
        assertEquals(1.0, savedEntry?.weightLegallyDiscardedKg)
    }

    @Test
    fun noSpeciesCheckedOnSaveShowsChecklistRequiredError() {
        var submitted: CatchRecordDraft? = null
        val gearUse = gearUseWithAddedSpecies(listOf(cod.id))
        composeTestRule.setContent {
            MmoTheme {
                GearSpeciesChecklistScreenContent(
                    draft = draftWith(gearUse),
                    gearUse = gearUse,
                    speciesList = speciesList,
                    onRemoveSpecies = {},
                    onAddAnotherSpecies = {},
                    onSubmit = { submitted = it },
                )
            }
        }

        composeTestRule.onNodeWithTag(GearSpeciesChecklistScreenTestTags.SAVE_ACTION).performClick()

        composeTestRule.onNodeWithTag(GearSpeciesChecklistScreenTestTags.CHECKLIST_ERROR).assertIsDisplayed()
        assertNull(submitted)
    }

    @Test
    fun blankMandatoryAboveMinimumFieldShowsRequiredErrorInSummary() {
        val gearUse = gearUseWithAddedSpecies(listOf(cod.id))
        composeTestRule.setContent {
            MmoTheme {
                GearSpeciesChecklistScreenContent(
                    draft = draftWith(gearUse),
                    gearUse = gearUse,
                    speciesList = speciesList,
                    onRemoveSpecies = {},
                    onAddAnotherSpecies = {},
                    onSubmit = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("${GearSpeciesChecklistScreenTestTags.CHECKBOX_PREFIX}_0").performClick()
        composeTestRule.onNodeWithTag(GearSpeciesChecklistScreenTestTags.SAVE_ACTION).performClick()

        composeTestRule.onNodeWithTag(GearSpeciesChecklistScreenTestTags.ERROR_SUMMARY).assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Enter the weight above minimum size retained").onFirst().assertIsDisplayed()
    }

    @Test
    fun optionalAboveMinimumFieldLeftBlankIsNotAnErrorForANonMandatorySpecies() {
        var submitted: CatchRecordDraft? = null
        val gearUse = gearUseWithAddedSpecies(listOf(plaice.id))
        composeTestRule.setContent {
            MmoTheme {
                GearSpeciesChecklistScreenContent(
                    draft = draftWith(gearUse),
                    gearUse = gearUse,
                    speciesList = speciesList,
                    onRemoveSpecies = {},
                    onAddAnotherSpecies = {},
                    onSubmit = { submitted = it },
                )
            }
        }

        composeTestRule.onNodeWithTag("${GearSpeciesChecklistScreenTestTags.CHECKBOX_PREFIX}_0").performClick()
        composeTestRule.onNodeWithTag(GearSpeciesChecklistScreenTestTags.SAVE_ACTION).performClick()

        assertTrue(submitted != null)
        val savedEntry =
            submitted
                ?.gearUses
                ?.single()
                ?.speciesWeights
                ?.single { it.speciesId == plaice.id }
        assertEquals(true, savedEntry?.confirmedCaught)
        assertNull(savedEntry?.weightAboveMinimumSizeKg)
    }

    @Test
    fun oneDecimalPlaceSpeciesRejectsATwoDecimalPlaceWeightWithAPrecisionError() {
        // Note: a WholeNumber species' field renders as `GdsNumericFieldKind.Integer`, which strips any
        // '.' the user types at input time (see `GdsNumericField.filterNumericInput`) — so a fractional
        // value can never actually reach the validator for a whole-number species via real typing, and
        // the Precision error for `SpeciesWeightPrecision.WholeNumber` is validator-level defensive-only
        // (covered by `SpeciesWeightValidatorTests`, not reachable here). The one reachable-via-typing
        // Precision path is `OneDecimalPlace`: the Decimal-kind field allows a single '.' but does not
        // limit how many digits follow it, so a two-decimal-place value like "5.55" is exactly what a
        // real user could type and still trip the precision rule.
        val gearUse = gearUseWithAddedSpecies(listOf(cod.id))
        composeTestRule.setContent {
            MmoTheme {
                GearSpeciesChecklistScreenContent(
                    draft = draftWith(gearUse),
                    gearUse = gearUse,
                    speciesList = speciesList,
                    onRemoveSpecies = {},
                    onAddAnotherSpecies = {},
                    onSubmit = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("${GearSpeciesChecklistScreenTestTags.CHECKBOX_PREFIX}_0").performClick()
        composeTestRule
            .onNodeWithTag("${GearSpeciesChecklistScreenTestTags.ABOVE_MIN_FIELD_PREFIX}_${cod.id}")
            .performTextInput("5.55")
        composeTestRule.onNodeWithTag(GearSpeciesChecklistScreenTestTags.SAVE_ACTION).performClick()

        composeTestRule
            .onAllNodesWithText("Weight for Atlantic cod must be a number with up to 1 decimal place")
            .onFirst()
            .assertIsDisplayed()
    }

    @Test
    fun weightExceedingTheMonthlyQuotaShowsAMonthlyQuotaError() {
        val gearUse = gearUseWithAddedSpecies(listOf(plaice.id))
        composeTestRule.setContent {
            MmoTheme {
                GearSpeciesChecklistScreenContent(
                    draft = draftWith(gearUse),
                    gearUse = gearUse,
                    speciesList = speciesList,
                    onRemoveSpecies = {},
                    onAddAnotherSpecies = {},
                    onSubmit = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("${GearSpeciesChecklistScreenTestTags.CHECKBOX_PREFIX}_0").performClick()
        composeTestRule
            .onNodeWithTag("${GearSpeciesChecklistScreenTestTags.ABOVE_MIN_FIELD_PREFIX}_${plaice.id}")
            .performTextInput("11")
        composeTestRule.onNodeWithTag(GearSpeciesChecklistScreenTestTags.SAVE_ACTION).performClick()

        composeTestRule
            .onAllNodesWithText("The weight entered would exceed your quota for this species. Enter a lower weight.")
            .onFirst()
            .assertIsDisplayed()
    }

    @Test
    fun zeroAboveMinimumFieldShowsBelowMinimumRangeError() {
        val gearUse = gearUseWithAddedSpecies(listOf(cod.id))
        composeTestRule.setContent {
            MmoTheme {
                GearSpeciesChecklistScreenContent(
                    draft = draftWith(gearUse),
                    gearUse = gearUse,
                    speciesList = speciesList,
                    onRemoveSpecies = {},
                    onAddAnotherSpecies = {},
                    onSubmit = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("${GearSpeciesChecklistScreenTestTags.CHECKBOX_PREFIX}_0").performClick()
        composeTestRule
            .onNodeWithTag("${GearSpeciesChecklistScreenTestTags.ABOVE_MIN_FIELD_PREFIX}_${cod.id}")
            .performTextInput("0")
        composeTestRule.onNodeWithTag(GearSpeciesChecklistScreenTestTags.SAVE_ACTION).performClick()

        composeTestRule
            .onAllNodesWithText("Weight for Atlantic cod must be more than 0kg")
            .onFirst()
            .assertIsDisplayed()
    }

    @Test
    fun aboveMinimumFieldOverTheMaximumShowsAboveMaximumRangeError() {
        val gearUse = gearUseWithAddedSpecies(listOf(cod.id))
        composeTestRule.setContent {
            MmoTheme {
                GearSpeciesChecklistScreenContent(
                    draft = draftWith(gearUse),
                    gearUse = gearUse,
                    speciesList = speciesList,
                    onRemoveSpecies = {},
                    onAddAnotherSpecies = {},
                    onSubmit = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("${GearSpeciesChecklistScreenTestTags.CHECKBOX_PREFIX}_0").performClick()
        composeTestRule
            .onNodeWithTag("${GearSpeciesChecklistScreenTestTags.ABOVE_MIN_FIELD_PREFIX}_${cod.id}")
            .performTextInput("10000.1")
        composeTestRule.onNodeWithTag(GearSpeciesChecklistScreenTestTags.SAVE_ACTION).performClick()

        composeTestRule
            .onAllNodesWithText("Weight for Atlantic cod must be 10,000kg or less")
            .onFirst()
            .assertIsDisplayed()
    }

    @Test
    fun removeASpeciesLinkIsDisabledWhenNothingIsCheckedButRemainsVisible() {
        val gearUse = gearUseWithAddedSpecies(listOf(cod.id))
        composeTestRule.setContent {
            MmoTheme {
                GearSpeciesChecklistScreenContent(
                    draft = draftWith(gearUse),
                    gearUse = gearUse,
                    speciesList = speciesList,
                    onRemoveSpecies = {},
                    onAddAnotherSpecies = {},
                    onSubmit = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(GearSpeciesChecklistScreenTestTags.REMOVE_SPECIES_ACTION)
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    @Test
    fun removeASpeciesLinkRemovesOnlyCheckedSpeciesFromTheGearUse() {
        var updated: CatchRecordDraft? = null
        val gearUse = gearUseWithAddedSpecies(listOf(cod.id, plaice.id))
        composeTestRule.setContent {
            MmoTheme {
                GearSpeciesChecklistScreenContent(
                    draft = draftWith(gearUse),
                    gearUse = gearUse,
                    speciesList = speciesList,
                    onRemoveSpecies = { updated = it },
                    onAddAnotherSpecies = {},
                    onSubmit = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("${GearSpeciesChecklistScreenTestTags.CHECKBOX_PREFIX}_0").performClick()
        composeTestRule.onNodeWithTag(GearSpeciesChecklistScreenTestTags.REMOVE_SPECIES_ACTION).performClick()

        val remainingSpeciesIds =
            updated
                ?.gearUses
                ?.single()
                ?.speciesWeights
                ?.map { it.speciesId }
        assertEquals(listOf(plaice.id), remainingSpeciesIds)
    }
}
