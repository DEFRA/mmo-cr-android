package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.CatchRecordDraft
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DraftStatus
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.MeasurementValue
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementField
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldKeys
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearType

class GearScreensTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val seineNets = GearType(id = "gear-seine-nets", name = "Seine nets (not specified)")
    private val bottomOtterTrawls =
        GearType(
            id = "gear-bottom-otter-trawls-tb",
            name = "Bottom otter trawls (TB)",
            measurementFields =
                listOf(
                    GearMeasurementField(
                        key = GearMeasurementFieldKeys.NUMBER_OF_TRAWL_NETS,
                        label = "Number of trawl nets",
                        type = GearMeasurementFieldType.Integer,
                    ),
                    GearMeasurementField(
                        key = GearMeasurementFieldKeys.MESH_SIZE_MM,
                        label = "Mesh size (mm)",
                        type = GearMeasurementFieldType.Integer,
                        unit = "mm",
                    ),
                ),
        )
    private val seineNetsWithSchema =
        seineNets.copy(
            measurementFields =
                listOf(
                    GearMeasurementField(
                        key = GearMeasurementFieldKeys.MESH_SIZE_MM,
                        label = "Mesh size (mm)",
                        type = GearMeasurementFieldType.Integer,
                        unit = "mm",
                    ),
                ),
        )
    private val gearTypes = listOf(seineNetsWithSchema, bottomOtterTrawls)

    private val potsOrTrapsMeasurementFields =
        listOf(
            GearMeasurementField(
                key = GearMeasurementFieldKeys.TOTAL_POTS_OR_TRAPS_HAULED,
                label = "Total pots or traps hauled",
                type = GearMeasurementFieldType.Integer,
            ),
            GearMeasurementField(
                key = GearMeasurementFieldKeys.TOTAL_POTS_OR_TRAPS_LEFT_IN_WATER,
                label = "Total pots or traps left in water",
                type = GearMeasurementFieldType.Integer,
            ),
        )
    private val pots = GearType(id = "gear-pots", name = "Pots", measurementFields = potsOrTrapsMeasurementFields)
    private val traps = GearType(id = "gear-traps", name = "Traps", measurementFields = potsOrTrapsMeasurementFields)
    private val handlines =
        GearType(
            id = "gear-handlines",
            name = "Handlines and pole lines (hand operated)",
            measurementFields =
                listOf(
                    GearMeasurementField(
                        key = GearMeasurementFieldKeys.NUMBER_OF_RODS_AND_LINES,
                        label = "Number of rods and lines",
                        type = GearMeasurementFieldType.Integer,
                    ),
                ),
            measurementTitleOverride = "handlines",
        )
    private val driftingLonglines =
        GearType(
            id = "gear-drifting-longlines",
            name = "Drifting longlines",
            measurementFields =
                listOf(
                    GearMeasurementField(
                        key = GearMeasurementFieldKeys.TOTAL_HOOKS_HAULED,
                        label = "Total hooks hauled",
                        type = GearMeasurementFieldType.Integer,
                    ),
                    GearMeasurementField(
                        key = GearMeasurementFieldKeys.TOTAL_HOOKS_LEFT_IN_WATER,
                        label = "Total hooks left in water",
                        type = GearMeasurementFieldType.Integer,
                    ),
                ),
        )
    private val gillnetsCircling =
        GearType(
            id = "gear-gillnets-circling",
            name = "Gillnets (circling)",
            measurementFields =
                listOf(
                    GearMeasurementField(
                        key = GearMeasurementFieldKeys.MESH_SIZE_MM,
                        label = "Mesh size (mm)",
                        type = GearMeasurementFieldType.Integer,
                        unit = "mm",
                    ),
                    GearMeasurementField(
                        key = GearMeasurementFieldKeys.TOTAL_LENGTH_OF_NETS_HAULED_M,
                        label = "Total length of nets hauled (m)",
                        type = GearMeasurementFieldType.Integer,
                        unit = "m",
                    ),
                    GearMeasurementField(
                        key = GearMeasurementFieldKeys.TOTAL_LENGTH_OF_NETS_LEFT_IN_WATER_M,
                        label = "Total length of nets left in water (m)",
                        type = GearMeasurementFieldType.Integer,
                        unit = "m",
                    ),
                ),
        )

    // --- Gear search --------------------------------------------------------------------------------

    @Test
    fun gearSearchGatesSuggestionsAndSubmitsSelectedGearTypeId() {
        var submittedId: String? = null
        composeTestRule.setContent {
            MmoTheme {
                GearSearchScreenContent(gearTypes = gearTypes, onSubmit = { submittedId = it })
            }
        }

        composeTestRule.onAllNodesWithTag("${GearSearchScreenTestTags.SUGGESTION_PREFIX}_0").assertCountEquals(0)
        composeTestRule.onNodeWithTag(GearSearchScreenTestTags.AUTOCOMPLETE_FIELD).performTextInput("Se")
        composeTestRule.onAllNodesWithTag("${GearSearchScreenTestTags.SUGGESTION_PREFIX}_0").assertCountEquals(1)
        composeTestRule.onNodeWithTag("${GearSearchScreenTestTags.SUGGESTION_PREFIX}_0").performClick()
        composeTestRule.onNodeWithTag(GearSearchScreenTestTags.SAVE_ACTION).performClick()
        assertEquals("gear-seine-nets", submittedId)
    }

    @Test
    fun gearSearchScreenShowsWhatGearDidYouUseHeadingWhenDraftHasNoGearYet() {
        val draft = sampleDraft(emptyList())
        val state = CatchRecordFlowViewState(status = UiStatus.Content(draft), gearTypes = gearTypes)
        composeTestRule.setContent {
            MmoTheme {
                GearSearchScreen(state = state, onSubmit = {}, onBack = {})
            }
        }

        composeTestRule.onNodeWithText("What gear did you use?").assertIsDisplayed()
    }

    @Test
    fun gearSearchScreenShowsAddGearToYourListHeadingWhenAddingAnotherGear() {
        val draft = sampleDraft(listOf(gearUseSeineNets))
        val state = CatchRecordFlowViewState(status = UiStatus.Content(draft), gearTypes = gearTypes)
        composeTestRule.setContent {
            MmoTheme {
                GearSearchScreen(state = state, onSubmit = {}, onBack = {})
            }
        }

        composeTestRule.onNodeWithText("Add gear to your list").assertIsDisplayed()
    }

    @Test
    fun gearSearchWithNoSelectionShowsErrorAndDoesNotSubmit() {
        var submittedId: String? = null
        composeTestRule.setContent {
            MmoTheme {
                GearSearchScreenContent(gearTypes = gearTypes, onSubmit = { submittedId = it })
            }
        }

        composeTestRule.onNodeWithTag(GearSearchScreenTestTags.SAVE_ACTION).performClick()
        composeTestRule.onNodeWithTag(GearSearchScreenTestTags.ERROR_MESSAGE).assertIsDisplayed()
        assertNull(submittedId)
    }

    // --- Gear measurement (generic, schema-driven) ---------------------------------------------------

    @Test
    fun seineNetsMeasurementScreenSubmitsMeshSizeOnly() {
        var submitted: Map<String, MeasurementValue>? = null
        composeTestRule.setContent {
            MmoTheme {
                GearMeasurementScreenContent(gearType = seineNetsWithSchema, onSubmit = { submitted = it })
            }
        }

        composeTestRule.onNodeWithTag("${GearMeasurementScreenTestTags.FIELD_PREFIX}_0").performTextInput("100")
        composeTestRule.onNodeWithTag(GearMeasurementScreenTestTags.SAVE_ACTION).performClick()
        assertEquals(
            MeasurementValue.Numeric(100.0, "mm"),
            submitted?.get(GearMeasurementFieldKeys.MESH_SIZE_MM),
        )
        assertEquals(1, submitted?.size)
    }

    @Test
    fun bottomOtterTrawlMeasurementScreenSubmitsBothFields() {
        var submitted: Map<String, MeasurementValue>? = null
        composeTestRule.setContent {
            MmoTheme {
                GearMeasurementScreenContent(gearType = bottomOtterTrawls, onSubmit = { submitted = it })
            }
        }

        composeTestRule.onNodeWithTag("${GearMeasurementScreenTestTags.FIELD_PREFIX}_0").performTextInput("2")
        composeTestRule.onNodeWithTag("${GearMeasurementScreenTestTags.FIELD_PREFIX}_1").performTextInput("80")
        composeTestRule.onNodeWithTag(GearMeasurementScreenTestTags.SAVE_ACTION).performClick()
        assertEquals(
            MeasurementValue.Numeric(2.0, ""),
            submitted?.get(GearMeasurementFieldKeys.NUMBER_OF_TRAWL_NETS),
        )
        assertEquals(
            MeasurementValue.Numeric(80.0, "mm"),
            submitted?.get(GearMeasurementFieldKeys.MESH_SIZE_MM),
        )
    }

    @Test
    fun measurementScreenShowsTheWholeNumberInstructionForEveryGearIncludingSeineNetsRetrofit() {
        composeTestRule.setContent {
            MmoTheme {
                GearMeasurementScreenContent(gearType = seineNetsWithSchema, onSubmit = {})
            }
        }

        composeTestRule.onNodeWithTag(GearMeasurementScreenTestTags.INSTRUCTION).assertIsDisplayed()
        composeTestRule
            .onNodeWithText("All gear measurements must be whole numbers.")
            .assertIsDisplayed()
    }

    @Test
    fun potsMeasurementScreenSubmitsBothWholeNumberFields() {
        var submitted: Map<String, MeasurementValue>? = null
        composeTestRule.setContent {
            MmoTheme {
                GearMeasurementScreenContent(gearType = pots, onSubmit = { submitted = it })
            }
        }

        composeTestRule.onNodeWithTag("${GearMeasurementScreenTestTags.FIELD_PREFIX}_0").performTextInput("20")
        composeTestRule.onNodeWithTag("${GearMeasurementScreenTestTags.FIELD_PREFIX}_1").performTextInput("5")
        composeTestRule.onNodeWithTag(GearMeasurementScreenTestTags.SAVE_ACTION).performClick()
        assertEquals(
            MeasurementValue.Numeric(20.0, ""),
            submitted?.get(GearMeasurementFieldKeys.TOTAL_POTS_OR_TRAPS_HAULED),
        )
        assertEquals(
            MeasurementValue.Numeric(5.0, ""),
            submitted?.get(GearMeasurementFieldKeys.TOTAL_POTS_OR_TRAPS_LEFT_IN_WATER),
        )
    }

    @Test
    fun trapsMeasurementScreenUsesTheSameTwoFieldSchemaAsPotsButIsADistinctGearType() {
        var submitted: Map<String, MeasurementValue>? = null
        composeTestRule.setContent {
            MmoTheme {
                GearMeasurementScreenContent(gearType = traps, onSubmit = { submitted = it })
            }
        }

        composeTestRule.onNodeWithTag("${GearMeasurementScreenTestTags.FIELD_PREFIX}_0").performTextInput("8")
        composeTestRule.onNodeWithTag("${GearMeasurementScreenTestTags.FIELD_PREFIX}_1").performTextInput("2")
        composeTestRule.onNodeWithTag(GearMeasurementScreenTestTags.SAVE_ACTION).performClick()
        assertEquals(
            MeasurementValue.Numeric(8.0, ""),
            submitted?.get(GearMeasurementFieldKeys.TOTAL_POTS_OR_TRAPS_HAULED),
        )
        assertEquals(
            MeasurementValue.Numeric(2.0, ""),
            submitted?.get(GearMeasurementFieldKeys.TOTAL_POTS_OR_TRAPS_LEFT_IN_WATER),
        )
        assertTrue(pots.id != traps.id)
    }

    @Test
    fun handlinesMeasurementScreenSubmitsSingleRodsAndLinesField() {
        var submitted: Map<String, MeasurementValue>? = null
        composeTestRule.setContent {
            MmoTheme {
                GearMeasurementScreenContent(gearType = handlines, onSubmit = { submitted = it })
            }
        }

        composeTestRule.onNodeWithTag("${GearMeasurementScreenTestTags.FIELD_PREFIX}_0").performTextInput("4")
        composeTestRule.onNodeWithTag(GearMeasurementScreenTestTags.SAVE_ACTION).performClick()
        assertEquals(
            MeasurementValue.Numeric(4.0, ""),
            submitted?.get(GearMeasurementFieldKeys.NUMBER_OF_RODS_AND_LINES),
        )
        assertEquals(1, submitted?.size)
    }

    @Test
    fun driftingLonglinesMeasurementScreenSubmitsBothHookFields() {
        var submitted: Map<String, MeasurementValue>? = null
        composeTestRule.setContent {
            MmoTheme {
                GearMeasurementScreenContent(gearType = driftingLonglines, onSubmit = { submitted = it })
            }
        }

        composeTestRule.onNodeWithTag("${GearMeasurementScreenTestTags.FIELD_PREFIX}_0").performTextInput("500")
        composeTestRule.onNodeWithTag("${GearMeasurementScreenTestTags.FIELD_PREFIX}_1").performTextInput("10")
        composeTestRule.onNodeWithTag(GearMeasurementScreenTestTags.SAVE_ACTION).performClick()
        assertEquals(
            MeasurementValue.Numeric(500.0, ""),
            submitted?.get(GearMeasurementFieldKeys.TOTAL_HOOKS_HAULED),
        )
        assertEquals(
            MeasurementValue.Numeric(10.0, ""),
            submitted?.get(GearMeasurementFieldKeys.TOTAL_HOOKS_LEFT_IN_WATER),
        )
    }

    @Test
    fun gillnetsMeasurementScreenSubmitsAllThreeWholeNumberFields() {
        var submitted: Map<String, MeasurementValue>? = null
        composeTestRule.setContent {
            MmoTheme {
                GearMeasurementScreenContent(gearType = gillnetsCircling, onSubmit = { submitted = it })
            }
        }

        composeTestRule.onNodeWithTag("${GearMeasurementScreenTestTags.FIELD_PREFIX}_0").performTextInput("60")
        composeTestRule.onNodeWithTag("${GearMeasurementScreenTestTags.FIELD_PREFIX}_1").performTextInput("500")
        composeTestRule.onNodeWithTag("${GearMeasurementScreenTestTags.FIELD_PREFIX}_2").performTextInput("50")
        composeTestRule.onNodeWithTag(GearMeasurementScreenTestTags.SAVE_ACTION).performClick()
        assertEquals(
            MeasurementValue.Numeric(60.0, "mm"),
            submitted?.get(GearMeasurementFieldKeys.MESH_SIZE_MM),
        )
        assertEquals(
            MeasurementValue.Numeric(500.0, "m"),
            submitted?.get(GearMeasurementFieldKeys.TOTAL_LENGTH_OF_NETS_HAULED_M),
        )
        assertEquals(
            MeasurementValue.Numeric(50.0, "m"),
            submitted?.get(GearMeasurementFieldKeys.TOTAL_LENGTH_OF_NETS_LEFT_IN_WATER_M),
        )
        assertEquals(3, submitted?.size)
    }

    @Test
    fun measurementScreenWithMissingRequiredValueShowsErrorSummaryAndDoesNotSubmit() {
        var submitted: Map<String, MeasurementValue>? = null
        composeTestRule.setContent {
            MmoTheme {
                GearMeasurementScreenContent(gearType = seineNetsWithSchema, onSubmit = { submitted = it })
            }
        }

        composeTestRule.onNodeWithTag(GearMeasurementScreenTestTags.SAVE_ACTION).performClick()
        composeTestRule.onNodeWithTag(GearMeasurementScreenTestTags.ERROR_SUMMARY).assertIsDisplayed()
        assertNull(submitted)
    }

    /**
     * [GdsNumericField] filters out non-digit/non-decimal-point characters as they are typed — in reality
     * this path is not reachable via a real device keyboard at all, since the field requests
     * [androidx.compose.ui.text.input.KeyboardType.Number] and the on-screen keyboard never offers letter
     * keys; [performTextInput] injects raw text directly via semantics, bypassing the IME. Injecting a
     * value that the filter fully rejects (leaving the field's composing/edit-processor state expecting
     * text that the composable then reverts to empty) is a known source of flakiness in the instrumented
     * Compose/IME interaction unrelated to app logic, so this test only asserts the filtering outcome
     * itself — the field's raw value — rather than driving a further Save-click + error-summary assertion
     * on top of it (that combination is what proved flaky here). The "Required"-vs-"Numeric" error paths
     * are exercised deterministically at the pure-validator level in [GearMeasurementInputValidatorTests],
     * and the "Numeric" error path is exercised through a real, reachable UI input (a lone decimal point)
     * in [measurementScreenWithALoneDecimalPointShowsErrorSummaryAndDoesNotSubmit] below.
     */
    @Test
    fun measurementScreenFiltersOutNonNumericCharactersAsTheyAreTyped() {
        composeTestRule.setContent {
            MmoTheme {
                GearMeasurementScreenContent(gearType = seineNetsWithSchema, onSubmit = {})
            }
        }

        composeTestRule.onNodeWithTag("${GearMeasurementScreenTestTags.FIELD_PREFIX}_0").performTextInput("abc")
        val fieldNode =
            composeTestRule.onNodeWithTag("${GearMeasurementScreenTestTags.FIELD_PREFIX}_0").fetchSemanticsNode()
        val editableText = fieldNode.config.getOrNull(SemanticsProperties.EditableText)?.text
        assertEquals("", editableText)
    }

    /**
     * A lone decimal point is the one value [GdsNumericField]'s character-level filtering still lets
     * through for a [GearMeasurementFieldType.Decimal] field (it permits a single '.' so the user can keep
     * typing a decimal), but it does not parse as a valid number — this reaches the validator's "Numeric"
     * error path via real UI input, rather than the "Required" path exercised above. No currently-confirmed
     * gear type uses a Decimal field any more (Seine nets/Bottom otter trawls' mesh size was retrofitted to
     * Integer once Gillnets confirmed every gear type's mesh size is whole-number — see
     * StubReferenceDataRepository), so this test uses a synthetic Decimal-typed gear type purely to keep
     * this UI-reachable "Numeric" error path covered for the schema type the validator still supports.
     */
    @Test
    fun measurementScreenWithALoneDecimalPointShowsErrorSummaryAndDoesNotSubmit() {
        val syntheticDecimalGearType =
            GearType(
                id = "gear-synthetic-decimal",
                name = "Synthetic decimal test gear",
                measurementFields =
                    listOf(
                        GearMeasurementField(
                            key = "generic_decimal_field",
                            label = "Generic decimal field",
                            type = GearMeasurementFieldType.Decimal,
                        ),
                    ),
            )
        var submitted: Map<String, MeasurementValue>? = null
        composeTestRule.setContent {
            MmoTheme {
                GearMeasurementScreenContent(gearType = syntheticDecimalGearType, onSubmit = { submitted = it })
            }
        }

        composeTestRule.onNodeWithTag("${GearMeasurementScreenTestTags.FIELD_PREFIX}_0").performTextInput(".")
        composeTestRule.onNodeWithTag(GearMeasurementScreenTestTags.SAVE_ACTION).performClick()
        composeTestRule.onNodeWithTag(GearMeasurementScreenTestTags.ERROR_SUMMARY).assertIsDisplayed()
        assertNull(submitted)
    }

    // --- Gear summary checklist -----------------------------------------------------------------------

    private val gearUseSeineNets =
        GearUse(
            id = "gear-use-1",
            gearTypeId = "gear-seine-nets",
            statRectangleId = null,
            measurements = mapOf(GearMeasurementFieldKeys.MESH_SIZE_MM to MeasurementValue.Numeric(100.0, "mm")),
        )
    private val gearUseBottomOtterTrawls =
        GearUse(
            id = "gear-use-2",
            gearTypeId = "gear-bottom-otter-trawls-tb",
            statRectangleId = null,
            measurements = mapOf(GearMeasurementFieldKeys.MESH_SIZE_MM to MeasurementValue.Numeric(80.0, "mm")),
        )

    private fun sampleDraft(gearUses: List<GearUse>) =
        CatchRecordDraft(
            id = "draft-1",
            vesselId = "vessel-1",
            gearUses = gearUses,
            modifiedAtEpochMillis = 1L,
            status = DraftStatus.Draft,
        )

    @Test
    fun gearSummaryShowsEachGearWithItsMeasurementSummary() {
        composeTestRule.setContent {
            MmoTheme {
                GearSummaryScreenContent(
                    draft = sampleDraft(listOf(gearUseSeineNets, gearUseBottomOtterTrawls)),
                    gearTypes = gearTypes,
                    onRemoveGear = {},
                    onAddAnotherGear = {},
                    onSubmit = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("${GearSummaryScreenTestTags.CHECKBOX_PREFIX}_0").assertIsOff()
        composeTestRule.onNodeWithTag("${GearSummaryScreenTestTags.CHECKBOX_PREFIX}_1").assertIsOff()
    }

    @Test
    fun checkingAGearRevealsItsShotsFieldAndSubmitConfirmsIt() {
        var submittedDraft: CatchRecordDraft? = null
        composeTestRule.setContent {
            MmoTheme {
                GearSummaryScreenContent(
                    draft = sampleDraft(listOf(gearUseSeineNets)),
                    gearTypes = gearTypes,
                    onRemoveGear = {},
                    onAddAnotherGear = {},
                    onSubmit = { submittedDraft = it },
                )
            }
        }

        composeTestRule
            .onAllNodesWithTag("${GearSummaryScreenTestTags.SHOTS_FIELD_PREFIX}_gear-use-1")
            .assertCountEquals(0)
        composeTestRule.onNodeWithTag("${GearSummaryScreenTestTags.CHECKBOX_PREFIX}_0").performClick()
        composeTestRule.onNodeWithTag("${GearSummaryScreenTestTags.CHECKBOX_PREFIX}_0").assertIsOn()
        composeTestRule
            .onNodeWithTag("${GearSummaryScreenTestTags.SHOTS_FIELD_PREFIX}_gear-use-1")
            .performTextInput("3")
        composeTestRule.onNodeWithTag(GearSummaryScreenTestTags.SAVE_ACTION).performClick()

        val gearUse = submittedDraft!!.gearUses.single()
        assertTrue(gearUse.confirmedUsedOnTrip)
        assertEquals(3, gearUse.numberOfShots)
    }

    @Test
    fun removeGearLinkIsDisabledWhenNothingIsCheckedAndEnabledWhenSomethingIsChecked() {
        composeTestRule.setContent {
            MmoTheme {
                GearSummaryScreenContent(
                    draft = sampleDraft(listOf(gearUseSeineNets, gearUseBottomOtterTrawls)),
                    gearTypes = gearTypes,
                    onRemoveGear = {},
                    onAddAnotherGear = {},
                    onSubmit = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(GearSummaryScreenTestTags.REMOVE_ACTION).assertIsNotEnabled()
        composeTestRule.onNodeWithTag("${GearSummaryScreenTestTags.CHECKBOX_PREFIX}_0").performClick()
        composeTestRule.onNodeWithTag(GearSummaryScreenTestTags.REMOVE_ACTION).assertIsEnabled()
    }

    @Test
    fun removeGearBulkRemovesOnlyCheckedGearUses() {
        var updatedDraft: CatchRecordDraft? = null
        composeTestRule.setContent {
            MmoTheme {
                GearSummaryScreenContent(
                    draft = sampleDraft(listOf(gearUseSeineNets, gearUseBottomOtterTrawls)),
                    gearTypes = gearTypes,
                    onRemoveGear = { updatedDraft = it },
                    onAddAnotherGear = {},
                    onSubmit = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("${GearSummaryScreenTestTags.CHECKBOX_PREFIX}_0").performClick()
        composeTestRule.onNodeWithTag(GearSummaryScreenTestTags.REMOVE_ACTION).performClick()

        assertEquals(listOf("gear-use-2"), updatedDraft?.gearUses?.map { it.id })
    }

    @Test
    fun addAnotherGearInvokesCallback() {
        var addAnotherInvoked = false
        composeTestRule.setContent {
            MmoTheme {
                GearSummaryScreenContent(
                    draft = sampleDraft(listOf(gearUseSeineNets)),
                    gearTypes = gearTypes,
                    onRemoveGear = {},
                    onAddAnotherGear = { addAnotherInvoked = true },
                    onSubmit = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(GearSummaryScreenTestTags.ADD_ANOTHER_ACTION).performClick()
        assertTrue(addAnotherInvoked)
    }

    /**
     * Explicitly requested rehydration test: mount the checklist screen from a saved draft with 2+ gears
     * already added, at least one checked (confirmed used on trip) with a shots value, and assert it
     * renders correctly — the same category of regression coverage as the Phase 1/2 date-field prefill
     * tests, proactively added rather than waiting for a bug report.
     */
    @Test
    fun gearSummaryRehydratesCheckedStateAndShotsValueFromASavedDraftAfterProcessDeath() {
        val rehydratedGearUses =
            listOf(
                gearUseSeineNets.copy(confirmedUsedOnTrip = true, numberOfShots = 4),
                gearUseBottomOtterTrawls,
            )
        composeTestRule.setContent {
            MmoTheme {
                GearSummaryScreenContent(
                    draft = sampleDraft(rehydratedGearUses),
                    gearTypes = gearTypes,
                    onRemoveGear = {},
                    onAddAnotherGear = {},
                    onSubmit = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("${GearSummaryScreenTestTags.CHECKBOX_PREFIX}_0").assertIsOn()
        composeTestRule.onNodeWithTag("${GearSummaryScreenTestTags.CHECKBOX_PREFIX}_1").assertIsOff()
        val shotsNode =
            composeTestRule
                .onNodeWithTag("${GearSummaryScreenTestTags.SHOTS_FIELD_PREFIX}_gear-use-1")
                .fetchSemanticsNode()
        val shotsText = shotsNode.config.getOrNull(SemanticsProperties.EditableText)?.text
        assertEquals("4", shotsText)
    }
}
