package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.landing

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.NotLandedSpeciesEntry
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.SpeciesWeightEntry
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.Species
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.SpeciesWeightPrecision

/** BR-XX (ADR 0014, Phase D) dirty-state coverage for [NotLandedStraightAwaySpeciesScreenContent]. */
@RunWith(RobolectricTestRunner::class)
class NotLandedStraightAwaySpeciesScreenContentDirtyTrackingTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val cod =
        Species(
            id = "species-cod",
            name = "Atlantic cod (COD)",
            faoCode = "COD",
            weightPrecision = SpeciesWeightPrecision.OneDecimalPlace,
        )

    private fun draftWithConfirmedSpecies(
        speciesIds: List<String>,
        notLandedEntries: List<NotLandedSpeciesEntry> = emptyList(),
    ) = CatchRecordDraft(
        id = "draft-1",
        vesselId = "vessel-1",
        gearUses =
            listOf(
                GearUse(
                    id = "gear-use-1",
                    gearTypeId = "gear-seine-nets",
                    statisticalSubRectangleCode = "38E95",
                    speciesWeights =
                        speciesIds.mapIndexed { index, id ->
                            SpeciesWeightEntry(id = "sw-$index", speciesId = id, confirmedCaught = true)
                        },
                    confirmedUsedOnTrip = true,
                ),
            ),
        notLandedSpeciesEntries = notLandedEntries,
        modifiedAtEpochMillis = 0L,
        status = DraftStatus.Draft,
    )

    @Test
    fun openingWithAnExistingWeightNeverReportsDirty() {
        var lastDirty = true
        val draft =
            draftWithConfirmedSpecies(
                listOf(cod.id),
                listOf(NotLandedSpeciesEntry(cod.id, weightAboveMinimumSizeKeptOnboardKg = 3.5)),
            )
        composeTestRule.setContent {
            MmoTheme {
                NotLandedStraightAwaySpeciesScreenContent(
                    draft = draft,
                    speciesList = listOf(cod),
                    onSubmit = {},
                    onDirtyChanged = { lastDirty = it },
                )
            }
        }

        assertFalse(lastDirty)
    }

    @Test
    fun editingTheKeptOnboardWeightReportsDirty() {
        var lastDirty = false
        val draft =
            draftWithConfirmedSpecies(
                listOf(cod.id),
                listOf(NotLandedSpeciesEntry(cod.id, weightAboveMinimumSizeKeptOnboardKg = 3.5)),
            )
        composeTestRule.setContent {
            MmoTheme {
                NotLandedStraightAwaySpeciesScreenContent(
                    draft = draft,
                    speciesList = listOf(cod),
                    onSubmit = {},
                    onDirtyChanged = { lastDirty = it },
                )
            }
        }

        composeTestRule
            .onNodeWithTag("${NotLandedStraightAwaySpeciesScreenTestTags.WEIGHT_FIELD_PREFIX}_${cod.id}")
            .performTextClearance()
        composeTestRule
            .onNodeWithTag("${NotLandedStraightAwaySpeciesScreenTestTags.WEIGHT_FIELD_PREFIX}_${cod.id}")
            .performTextInput("4")
        composeTestRule.waitForIdle()

        assertTrue(lastDirty)
    }

    @Test
    fun reclearingTheWeightBackToTheSavedValueReportsClean() {
        var lastDirty = false
        val draft =
            draftWithConfirmedSpecies(
                listOf(cod.id),
                listOf(NotLandedSpeciesEntry(cod.id, weightAboveMinimumSizeKeptOnboardKg = 3.5)),
            )
        composeTestRule.setContent {
            MmoTheme {
                NotLandedStraightAwaySpeciesScreenContent(
                    draft = draft,
                    speciesList = listOf(cod),
                    onSubmit = {},
                    onDirtyChanged = { lastDirty = it },
                )
            }
        }

        composeTestRule
            .onNodeWithTag("${NotLandedStraightAwaySpeciesScreenTestTags.WEIGHT_FIELD_PREFIX}_${cod.id}")
            .performTextClearance()
        composeTestRule
            .onNodeWithTag("${NotLandedStraightAwaySpeciesScreenTestTags.WEIGHT_FIELD_PREFIX}_${cod.id}")
            .performTextInput("3.5")
        composeTestRule.waitForIdle()

        assertFalse(lastDirty)
    }

    @Test
    fun togglingTheChecklistSelectionAloneDoesNotReportDirty() {
        var lastDirty = true
        val draft = draftWithConfirmedSpecies(listOf(cod.id))
        composeTestRule.setContent {
            MmoTheme {
                NotLandedStraightAwaySpeciesScreenContent(
                    draft = draft,
                    speciesList = listOf(cod),
                    onSubmit = {},
                    onDirtyChanged = { lastDirty = it },
                )
            }
        }

        composeTestRule.onNodeWithTag("${NotLandedStraightAwaySpeciesScreenTestTags.CHECKBOX_PREFIX}_0").performClick()
        composeTestRule.waitForIdle()

        assertFalse(lastDirty)
    }

    @Test
    fun savingNavigatesToAFreshInstanceThatStartsClean() {
        var lastDirty = false
        composeTestRule.setContent {
            MmoTheme {
                var submitted by remember { mutableStateOf<CatchRecordDraft?>(null) }
                if (submitted == null) {
                    NotLandedStraightAwaySpeciesScreenContent(
                        draft = draftWithConfirmedSpecies(listOf(cod.id)),
                        speciesList = listOf(cod),
                        onSubmit = { submitted = it },
                        onDirtyChanged = { lastDirty = it },
                    )
                } else {
                    // A fresh instance for the re-entered step — see DepartureDateScreen's equivalent comment.
                    NotLandedStraightAwaySpeciesScreenContent(
                        draft = requireNotNull(submitted),
                        speciesList = listOf(cod),
                        onSubmit = {},
                        onDirtyChanged = { lastDirty = it },
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("${NotLandedStraightAwaySpeciesScreenTestTags.CHECKBOX_PREFIX}_0").performClick()
        composeTestRule
            .onNodeWithTag("${NotLandedStraightAwaySpeciesScreenTestTags.WEIGHT_FIELD_PREFIX}_${cod.id}")
            .performTextInput("4")
        composeTestRule.waitForIdle()
        assertTrue(lastDirty)
        composeTestRule.onNodeWithTag(NotLandedStraightAwaySpeciesScreenTestTags.SAVE_ACTION).performClick()
        composeTestRule.waitForIdle()

        assertFalse(lastDirty)
    }
}
