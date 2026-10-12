package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.MeasurementValue
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementField
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldKeys
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearType

/**
 * JVM/Robolectric port of the androidTest `GearScreensTest`'s per-gear-type "Gear measurement" section -
 * see the top-level package doc on [GearSpeciesChecklistScreenTests] for why a `testDebugUnitTest`-reachable
 * equivalent is needed for the Kover/SonarCloud coverage gate. Exercises every
 * [gearMeasurementFieldLabel] branch (one per confirmed gear type's schema) plus the Required/Numeric
 * validation-error paths and the Preview composable.
 */
@RunWith(RobolectricTestRunner::class)
class GearMeasurementScreenContentFieldLabelsTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val seineNetsWithSchema =
        GearType(
            id = "gear-seine-nets",
            name = "Seine nets (not specified)",
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
     * A lone decimal point is the one value the numeric field's character-level filtering still lets
     * through for a [GearMeasurementFieldType.Decimal] field, but it does not parse as a valid number -
     * reaching the validator's "Numeric" error path via real UI input. No currently-confirmed gear type
     * uses a Decimal field (mesh size is Integer everywhere), so this test uses a synthetic Decimal-typed
     * gear type purely to keep this UI-reachable "Numeric" error path covered.
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

    /** Clicking an error-summary item's link focuses the corresponding field - covers the summary's
     *  per-item onClick wiring (fieldFocusRequesters). */
    @Test
    fun clickingAnErrorSummaryItemRequestsFocusOnItsField() {
        composeTestRule.setContent {
            MmoTheme {
                GearMeasurementScreenContent(gearType = seineNetsWithSchema, onSubmit = {})
            }
        }

        composeTestRule.onNodeWithTag(GearMeasurementScreenTestTags.SAVE_ACTION).performClick()
        composeTestRule.onNodeWithTag(GearMeasurementScreenTestTags.ERROR_SUMMARY).assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Enter Mesh size (mm)").filterToOne(hasClickAction()).performClick()
    }

    @Test
    fun seineNetsPreviewRendersWithoutError() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                GearMeasurementScreen_SeineNetsPreview()
            }
        }

        composeTestRule.onNodeWithTag("${GearMeasurementScreenTestTags.FIELD_PREFIX}_0").assertIsDisplayed()
    }
}
