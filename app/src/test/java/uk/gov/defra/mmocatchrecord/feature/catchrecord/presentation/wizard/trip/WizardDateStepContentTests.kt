package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.trip

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DmyDate
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class WizardDateStepContentTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val tags = DepartureDateScreenTestTags.stepTags
    private val today = LocalDate.of(2026, 10, 16)

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
                    today = today,
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
    fun openingAnEmptyStepNeverReportsDirty() {
        var lastDirty = true
        setContent(initialDate = null, onDirtyChanged = { lastDirty = it })

        assertFalse(lastDirty)
    }

    @Test
    fun movingAWheelReportsDirty() {
        var lastDirty = false
        setContent(initialDate = DmyDate(3, 7, 2026), onDirtyChanged = { lastDirty = it })

        composeTestRule.setWheel(tags.dayWheel, 4)
        composeTestRule.waitForIdle()

        assertTrue(lastDirty)
    }

    @Test
    fun movingAWheelBackToTheSavedDateReportsClean() {
        var lastDirty = false
        setContent(initialDate = DmyDate(3, 7, 2026), onDirtyChanged = { lastDirty = it })

        composeTestRule.setWheel(tags.dayWheel, 4)
        composeTestRule.waitForIdle()
        assertTrue(lastDirty)
        composeTestRule.setWheel(tags.dayWheel, 3)
        composeTestRule.waitForIdle()

        assertFalse(lastDirty)
    }

    @Test
    fun selectingADifferentDateStaysDirtyUntilSaved() {
        var lastDirty = false
        setContent(initialDate = DmyDate(3, 7, 2026), onDirtyChanged = { lastDirty = it })

        composeTestRule.spinTo(tags, DmyDate(4, 7, 2026))
        composeTestRule.waitForIdle()

        assertTrue(lastDirty)
    }

    @Test
    fun previewRendersWithoutError() {
        // WizardDateStepContentPreview otherwise stays uncounted new code (no coverage merged
        // from connectedDebugAndroidTest) — see GdsAutocompleteFieldTests's equivalent doc comment.
        composeTestRule.setContent { WizardDateStepContentPreview() }

        // The preview hardcodes its own tags ("save" etc.), distinct from the outer `tags` fixture.
        composeTestRule.onNodeWithTag("save").assertIsDisplayed()
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
                        today = today,
                    )
                } else {
                    // A fresh instance mirrors the real screen recomposing from Loading to Content(saved).
                    WizardDateStepContent(
                        initialDate = savedDate,
                        testTags = tags,
                        onSubmit = {},
                        onDirtyChanged = { lastDirty = it },
                        today = today,
                    )
                }
            }
        }

        composeTestRule.spinTo(tags, DmyDate(11, 9, 2026))
        composeTestRule.waitForIdle()
        assertTrue(lastDirty)
        composeTestRule.onNodeWithTag(tags.saveAction).performClick()
        composeTestRule.waitForIdle()

        assertFalse(lastDirty)
    }
}
