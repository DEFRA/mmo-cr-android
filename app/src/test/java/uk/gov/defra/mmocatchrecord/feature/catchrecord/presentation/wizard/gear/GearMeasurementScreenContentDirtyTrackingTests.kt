package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear

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
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.MeasurementValue
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementField
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldKeys
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearMeasurementFieldType
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.referencedata.GearType

/** BR-XX (ADR 0014, Phase D) dirty-state coverage for [GearMeasurementScreenContent]'s [onDirtyChanged]. */
@RunWith(RobolectricTestRunner::class)
class GearMeasurementScreenContentDirtyTrackingTests {
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

    private fun setContent(
        initialMeasurements: Map<String, MeasurementValue>,
        onDirtyChanged: (Boolean) -> Unit,
    ) {
        composeTestRule.setContent {
            MmoTheme {
                GearMeasurementScreenContent(
                    gearType = seineNetsWithSchema,
                    initialMeasurements = initialMeasurements,
                    onSubmit = {},
                    onDirtyChanged = onDirtyChanged,
                )
            }
        }
    }

    @Test
    fun openingAPrefilledMeasurementNeverReportsDirty() {
        var lastDirty = true
        setContent(
            initialMeasurements = mapOf(GearMeasurementFieldKeys.MESH_SIZE_MM to MeasurementValue.Numeric(100.0, "mm")),
            onDirtyChanged = { lastDirty = it },
        )

        assertFalse(lastDirty)
    }

    @Test
    fun editingTheMeasurementReportsDirty() {
        var lastDirty = false
        setContent(
            initialMeasurements = mapOf(GearMeasurementFieldKeys.MESH_SIZE_MM to MeasurementValue.Numeric(100.0, "mm")),
            onDirtyChanged = { lastDirty = it },
        )

        composeTestRule.onNodeWithTag("${GearMeasurementScreenTestTags.FIELD_PREFIX}_0").performTextClearance()
        composeTestRule.onNodeWithTag("${GearMeasurementScreenTestTags.FIELD_PREFIX}_0").performTextInput("120")
        composeTestRule.waitForIdle()

        assertTrue(lastDirty)
    }

    @Test
    fun reclearingBackToTheSavedValueReportsClean() {
        var lastDirty = false
        setContent(
            initialMeasurements = mapOf(GearMeasurementFieldKeys.MESH_SIZE_MM to MeasurementValue.Numeric(100.0, "mm")),
            onDirtyChanged = { lastDirty = it },
        )

        composeTestRule.onNodeWithTag("${GearMeasurementScreenTestTags.FIELD_PREFIX}_0").performTextClearance()
        composeTestRule.onNodeWithTag("${GearMeasurementScreenTestTags.FIELD_PREFIX}_0").performTextInput("100")
        composeTestRule.waitForIdle()

        assertFalse(lastDirty)
    }

    @Test
    fun savingNavigatesToAFreshInstanceThatStartsClean() {
        var lastDirty = false
        composeTestRule.setContent {
            MmoTheme {
                var submitted by remember { mutableStateOf<Map<String, MeasurementValue>?>(null) }
                if (submitted == null) {
                    GearMeasurementScreenContent(
                        gearType = seineNetsWithSchema,
                        onSubmit = { submitted = it },
                        onDirtyChanged = { lastDirty = it },
                    )
                } else {
                    // A fresh instance for the re-entered step — see DepartureDateScreen's equivalent comment.
                    GearMeasurementScreenContent(
                        gearType = seineNetsWithSchema,
                        initialMeasurements = submitted.orEmpty(),
                        onSubmit = {},
                        onDirtyChanged = { lastDirty = it },
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("${GearMeasurementScreenTestTags.FIELD_PREFIX}_0").performTextInput("100")
        composeTestRule.waitForIdle()
        assertTrue(lastDirty)
        composeTestRule.onNodeWithTag(GearMeasurementScreenTestTags.SAVE_ACTION).performClick()
        composeTestRule.waitForIdle()

        assertFalse(lastDirty)
    }
}
