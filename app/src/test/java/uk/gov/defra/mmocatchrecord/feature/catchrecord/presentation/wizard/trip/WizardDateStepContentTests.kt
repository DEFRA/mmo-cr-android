package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.trip

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
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DmyDate

/** BR-XX (ADR 0014, Phase D) dirty-state coverage for [WizardDateStepContent]'s [onDirtyChanged] reporting. */
@RunWith(RobolectricTestRunner::class)
class WizardDateStepContentTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val tags =
        WizardDateStepTestTags(
            summary = DepartureDateScreenTestTags.ERROR_SUMMARY,
            dayField = DepartureDateScreenTestTags.DAY_FIELD,
            monthField = DepartureDateScreenTestTags.MONTH_FIELD,
            yearField = DepartureDateScreenTestTags.YEAR_FIELD,
            saveAction = DepartureDateScreenTestTags.SAVE_ACTION,
        )

    private fun setContent(
        initialDate: DmyDate?,
        onDirtyChanged: (Boolean) -> Unit,
    ) {
        composeTestRule.setContent {
            MmoTheme {
                WizardDateStepContent(
                    initialDate = initialDate,
                    testTags = tags,
                    onSubmit = {},
                    onDirtyChanged = onDirtyChanged,
                )
            }
        }
    }

    @Test
    fun openingAPrefilledStepNeverReportsDirty() {
        var lastDirty = true
        setContent(initialDate = DmyDate(3, 7, 2026), onDirtyChanged = { lastDirty = it })

        assertFalse(lastDirty)
    }

    @Test
    fun editingAFieldReportsDirty() {
        var lastDirty = false
        setContent(initialDate = DmyDate(3, 7, 2026), onDirtyChanged = { lastDirty = it })

        composeTestRule.onNodeWithTag(DepartureDateScreenTestTags.DAY_FIELD).performTextClearance()
        composeTestRule.onNodeWithTag(DepartureDateScreenTestTags.DAY_FIELD).performTextInput("04")
        composeTestRule.waitForIdle()

        assertTrue(lastDirty)
    }

    @Test
    fun reclearingBackToTheSavedValueReportsClean() {
        var lastDirty = false
        setContent(initialDate = DmyDate(3, 7, 2026), onDirtyChanged = { lastDirty = it })

        composeTestRule.onNodeWithTag(DepartureDateScreenTestTags.DAY_FIELD).performTextClearance()
        composeTestRule.onNodeWithTag(DepartureDateScreenTestTags.DAY_FIELD).performTextInput("03")
        composeTestRule.waitForIdle()

        assertFalse(lastDirty)
    }

    @Test
    fun savingNavigatesToAFreshInstanceThatStartsClean() {
        var lastDirty = false
        composeTestRule.setContent {
            MmoTheme {
                var savedDate by remember { mutableStateOf<DmyDate?>(null) }
                if (savedDate == null) {
                    WizardDateStepContent(
                        initialDate = null,
                        testTags = tags,
                        onSubmit = { savedDate = it },
                        onDirtyChanged = { lastDirty = it },
                    )
                } else {
                    // A fresh instance for the re-entered step, mirroring the real screen's ViewModel-driven
                    // recomposition from UiStatus.Loading to UiStatus.Content(saved) — see DepartureDateScreen.
                    WizardDateStepContent(
                        initialDate = savedDate,
                        testTags = tags,
                        onSubmit = {},
                        onDirtyChanged = { lastDirty = it },
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag(DepartureDateScreenTestTags.DAY_FIELD).performTextInput("11")
        composeTestRule.onNodeWithTag(DepartureDateScreenTestTags.MONTH_FIELD).performTextInput("09")
        composeTestRule.onNodeWithTag(DepartureDateScreenTestTags.YEAR_FIELD).performTextInput("2026")
        composeTestRule.waitForIdle()
        assertTrue(lastDirty)
        composeTestRule.onNodeWithTag(DepartureDateScreenTestTags.SAVE_ACTION).performClick()
        composeTestRule.waitForIdle()

        assertFalse(lastDirty)
    }
}
