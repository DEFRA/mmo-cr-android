package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DmyDate

class DateScreensTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun departureDateHappyPathSubmitsParsedDate() {
        var submittedDate: DmyDate? = null
        composeTestRule.setContent {
            MmoTheme {
                WizardDateStepContent(
                    initialDate = null,
                    testTags =
                        WizardDateStepTestTags(
                            summary = DepartureDateScreenTestTags.ERROR_SUMMARY,
                            dayField = DepartureDateScreenTestTags.DAY_FIELD,
                            monthField = DepartureDateScreenTestTags.MONTH_FIELD,
                            yearField = DepartureDateScreenTestTags.YEAR_FIELD,
                            saveAction = DepartureDateScreenTestTags.SAVE_ACTION,
                        ),
                    onSubmit = { submittedDate = it },
                )
            }
        }

        composeTestRule.onNodeWithTag(DepartureDateScreenTestTags.DAY_FIELD).performTextInput("11")
        composeTestRule.onNodeWithTag(DepartureDateScreenTestTags.MONTH_FIELD).performTextInput("09")
        composeTestRule.onNodeWithTag(DepartureDateScreenTestTags.YEAR_FIELD).performTextInput("2026")
        composeTestRule.onNodeWithTag(DepartureDateScreenTestTags.SAVE_ACTION).performClick()
        assertEquals(DmyDate(11, 9, 2026), submittedDate)
    }

    @Test
    fun departureDateInvalidSubmitShowsErrorSummary() {
        var submittedDate: DmyDate? = null
        composeTestRule.setContent {
            MmoTheme {
                WizardDateStepContent(
                    initialDate = null,
                    testTags =
                        WizardDateStepTestTags(
                            summary = DepartureDateScreenTestTags.ERROR_SUMMARY,
                            dayField = DepartureDateScreenTestTags.DAY_FIELD,
                            monthField = DepartureDateScreenTestTags.MONTH_FIELD,
                            yearField = DepartureDateScreenTestTags.YEAR_FIELD,
                            saveAction = DepartureDateScreenTestTags.SAVE_ACTION,
                        ),
                    onSubmit = { submittedDate = it },
                )
            }
        }

        composeTestRule.onNodeWithTag(DepartureDateScreenTestTags.DAY_FIELD).performTextInput("31")
        composeTestRule.onNodeWithTag(DepartureDateScreenTestTags.MONTH_FIELD).performTextInput("02")
        composeTestRule.onNodeWithTag(DepartureDateScreenTestTags.YEAR_FIELD).performTextInput("2026")
        composeTestRule.onNodeWithTag(DepartureDateScreenTestTags.SAVE_ACTION).performClick()
        composeTestRule.onNodeWithTag(DepartureDateScreenTestTags.ERROR_SUMMARY).assertIsDisplayed()
        assertNull(submittedDate)
    }

    @Test
    fun returnDateBeforeDepartureShowsErrorSummary() {
        var submittedDate: DmyDate? = null
        composeTestRule.setContent {
            MmoTheme {
                WizardDateStepContent(
                    initialDate = null,
                    departureDate = DmyDate(11, 9, 2026),
                    isReturnDate = true,
                    testTags =
                        WizardDateStepTestTags(
                            summary = ReturnDateScreenTestTags.ERROR_SUMMARY,
                            dayField = ReturnDateScreenTestTags.DAY_FIELD,
                            monthField = ReturnDateScreenTestTags.MONTH_FIELD,
                            yearField = ReturnDateScreenTestTags.YEAR_FIELD,
                            saveAction = ReturnDateScreenTestTags.SAVE_ACTION,
                        ),
                    onSubmit = { submittedDate = it },
                )
            }
        }

        composeTestRule.onNodeWithTag(ReturnDateScreenTestTags.DAY_FIELD).performTextInput("10")
        composeTestRule.onNodeWithTag(ReturnDateScreenTestTags.MONTH_FIELD).performTextInput("09")
        composeTestRule.onNodeWithTag(ReturnDateScreenTestTags.YEAR_FIELD).performTextInput("2026")
        composeTestRule.onNodeWithTag(ReturnDateScreenTestTags.SAVE_ACTION).performClick()
        composeTestRule.onNodeWithTag(ReturnDateScreenTestTags.ERROR_SUMMARY).assertIsDisplayed()
        assertNull(submittedDate)
    }

    /**
     * Regression test: when this step is re-entered with an already-saved value (via Back, draft
     * resume/rehydration, or a future "Change" link), the Day/Month/Year fields must show that saved
     * value pre-filled — not blank — matching the "Which day did you set off/return on your trip?"
     * re-display state confirmed against Figma (as distinct from the first-time-entry "Which date..."
     * empty state). Also asserts day/month are zero-padded to two digits (e.g. "03"), per GDS convention.
     */
    @Test
    fun departureDateStepPrefillsFieldsFromPreviouslySavedValue() {
        composeTestRule.setContent {
            MmoTheme {
                WizardDateStepContent(
                    initialDate = DmyDate(3, 7, 2026),
                    testTags =
                        WizardDateStepTestTags(
                            summary = DepartureDateScreenTestTags.ERROR_SUMMARY,
                            dayField = DepartureDateScreenTestTags.DAY_FIELD,
                            monthField = DepartureDateScreenTestTags.MONTH_FIELD,
                            yearField = DepartureDateScreenTestTags.YEAR_FIELD,
                            saveAction = DepartureDateScreenTestTags.SAVE_ACTION,
                        ),
                    onSubmit = {},
                )
            }
        }

        assertEditableText(DepartureDateScreenTestTags.DAY_FIELD, "03")
        assertEditableText(DepartureDateScreenTestTags.MONTH_FIELD, "07")
        assertEditableText(DepartureDateScreenTestTags.YEAR_FIELD, "2026")
    }

    @Test
    fun returnDateStepPrefillsFieldsFromPreviouslySavedValue() {
        composeTestRule.setContent {
            MmoTheme {
                WizardDateStepContent(
                    initialDate = DmyDate(5, 7, 2026),
                    departureDate = DmyDate(3, 7, 2026),
                    isReturnDate = true,
                    testTags =
                        WizardDateStepTestTags(
                            summary = ReturnDateScreenTestTags.ERROR_SUMMARY,
                            dayField = ReturnDateScreenTestTags.DAY_FIELD,
                            monthField = ReturnDateScreenTestTags.MONTH_FIELD,
                            yearField = ReturnDateScreenTestTags.YEAR_FIELD,
                            saveAction = ReturnDateScreenTestTags.SAVE_ACTION,
                        ),
                    onSubmit = {},
                )
            }
        }

        assertEditableText(ReturnDateScreenTestTags.DAY_FIELD, "05")
        assertEditableText(ReturnDateScreenTestTags.MONTH_FIELD, "07")
        assertEditableText(ReturnDateScreenTestTags.YEAR_FIELD, "2026")
    }

    /**
     * Regression test for the same "re-entered step shows saved value" contract, but exercised the way it
     * happens in the app: the field is initially blank (first-time entry), the user saves a value, then the
     * step is torn down and a brand-new [WizardDateStepContent] instance is composed for it again (mirroring
     * a fresh composition after Back-navigation/draft-resume, as opposed to the same instance retaining
     * in-memory `remember` state) — and this fresh instance must show the previously saved value pre-filled,
     * not blank.
     */
    @Test
    fun departureDateStepShowsSavedValueWhenStepIsReenteredWithFreshComposition() {
        var savedDate: DmyDate? = null
        composeTestRule.setContent {
            MmoTheme {
                if (savedDate == null) {
                    WizardDateStepContent(
                        initialDate = null,
                        testTags =
                            WizardDateStepTestTags(
                                summary = DepartureDateScreenTestTags.ERROR_SUMMARY,
                                dayField = DepartureDateScreenTestTags.DAY_FIELD,
                                monthField = DepartureDateScreenTestTags.MONTH_FIELD,
                                yearField = DepartureDateScreenTestTags.YEAR_FIELD,
                                saveAction = DepartureDateScreenTestTags.SAVE_ACTION,
                            ),
                        onSubmit = { savedDate = it },
                    )
                } else {
                    // Simulates re-entering the step later with the ViewModel's now-rehydrated/saved value —
                    // a fresh composable instance, not the one that was just typed into.
                    WizardDateStepContent(
                        initialDate = savedDate,
                        testTags =
                            WizardDateStepTestTags(
                                summary = DepartureDateScreenTestTags.ERROR_SUMMARY,
                                dayField = DepartureDateScreenTestTags.DAY_FIELD,
                                monthField = DepartureDateScreenTestTags.MONTH_FIELD,
                                yearField = DepartureDateScreenTestTags.YEAR_FIELD,
                                saveAction = DepartureDateScreenTestTags.SAVE_ACTION,
                            ),
                        onSubmit = {},
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag(DepartureDateScreenTestTags.DAY_FIELD).performTextInput("03")
        composeTestRule.onNodeWithTag(DepartureDateScreenTestTags.MONTH_FIELD).performTextInput("07")
        composeTestRule.onNodeWithTag(DepartureDateScreenTestTags.YEAR_FIELD).performTextInput("2026")
        composeTestRule.onNodeWithTag(DepartureDateScreenTestTags.SAVE_ACTION).performClick()
        assertEquals(DmyDate(3, 7, 2026), savedDate)

        assertEditableText(DepartureDateScreenTestTags.DAY_FIELD, "03")
        assertEditableText(DepartureDateScreenTestTags.MONTH_FIELD, "07")
        assertEditableText(DepartureDateScreenTestTags.YEAR_FIELD, "2026")
    }

    private fun assertEditableText(
        testTag: String,
        expected: String,
    ) {
        val node = composeTestRule.onNodeWithTag(testTag).fetchSemanticsNode()
        val editableText = node.config.getOrNull(SemanticsProperties.EditableText)?.text
        assertEquals(expected, editableText)
    }
}
