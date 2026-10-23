package uk.gov.defra.mmocatchrecord.common.design

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class GdsDateWheelPickerTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val tags =
        GdsDateWheelPickerTestTags(
            monthWheel = "month",
            dayWheel = "day",
            yearWheel = "year",
        )

    private var latest: LocalDate? = null

    private fun setContent(initial: LocalDate) {
        composeTestRule.setContent {
            MmoTheme {
                var value by remember { mutableStateOf(initial) }
                GdsDateWheelPicker(
                    value = value,
                    onValueChange = {
                        value = it
                        latest = it
                    },
                    yearRange = 2025..2027,
                    testTags = tags,
                )
            }
        }
    }

    private fun setWheel(
        tag: String,
        value: Int,
    ) {
        composeTestRule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.SetProgress) { it(value.toFloat()) }
        composeTestRule.waitForIdle()
    }

    @Test
    fun eachWheelAnnouncesItsLabelAndCurrentValue() {
        setContent(LocalDate.of(2026, 1, 31))

        composeTestRule.onNodeWithTag("month").assert(hasDescriptionAndState("Month", "January"))
        composeTestRule.onNodeWithTag("day").assert(hasDescriptionAndState("Day", "31"))
        composeTestRule.onNodeWithTag("year").assert(hasDescriptionAndState("Year", "2026"))
    }

    @Test
    fun adjustingAWheelUpdatesTheDate() {
        setContent(LocalDate.of(2026, 1, 31))

        setWheel("year", 2025)
        setWheel("day", 15)

        assertEquals(LocalDate.of(2025, 1, 15), latest)
    }

    @Test
    fun changingToAShorterMonthClampsTheDay() {
        setContent(LocalDate.of(2026, 1, 31))

        setWheel("month", 2)

        assertEquals(LocalDate.of(2026, 2, 28), latest)
        composeTestRule.onNodeWithTag("day").assert(hasDescriptionAndState("Day", "28"))
    }

    @Test
    fun adjustingOutsideTheRangeIsCoerced() {
        setContent(LocalDate.of(2026, 1, 31))

        setWheel("year", 2030)

        assertEquals(LocalDate.of(2027, 1, 31), latest)
    }

    @Test
    fun tappingTheRowBelowSelectsTheNextValueWithoutDragging() {
        setContent(LocalDate.of(2026, 1, 30))

        composeTestRule.onNodeWithTag("day").performTouchInput { click(Offset(centerX, height * 5f / 6f)) }
        composeTestRule.waitForIdle()

        assertEquals(LocalDate.of(2026, 1, 31), latest)
    }

    @Test
    fun noOkOrCancelButtonsAreShown() {
        setContent(LocalDate.of(2026, 1, 31))

        composeTestRule.onNodeWithText("OK").assertDoesNotExist()
        composeTestRule.onNodeWithText("Cancel").assertDoesNotExist()
    }

    @Test
    fun arrowKeysStepAFocusedWheelAndStopAtTheEnds() {
        setContent(LocalDate.of(2026, 1, 2))

        composeTestRule.onNodeWithTag("day").requestFocus()
        composeTestRule.onNodeWithTag("day").performKeyInput { pressKey(Key.DirectionDown) }
        composeTestRule.waitForIdle()
        assertEquals(LocalDate.of(2026, 1, 3), latest)

        repeat(3) {
            composeTestRule.onNodeWithTag("day").performKeyInput { pressKey(Key.DirectionUp) }
            composeTestRule.waitForIdle()
        }
        assertEquals(LocalDate.of(2026, 1, 1), latest)
        composeTestRule.onNodeWithTag("day").performKeyInput { pressKey(Key.A) }
        assertEquals(LocalDate.of(2026, 1, 1), latest)
    }

    @Test
    fun swipingAWheelSettlesOnALaterValue() {
        setContent(LocalDate.of(2026, 1, 1))

        composeTestRule.onNodeWithTag("day").performTouchInput { swipeUp(startY = bottom, endY = centerY) }
        composeTestRule.waitForIdle()

        val settled = latest
        assertNotNull(settled)
        assertTrue(settled!!.dayOfMonth > 1)
        composeTestRule.onNodeWithTag("day").assert(hasDescriptionAndState("Day", settled.dayOfMonth.toString()))
    }

    @Test
    fun anExternalValueChangeScrollsTheWheelToMatch() {
        var external by mutableStateOf(LocalDate.of(2026, 1, 1))
        composeTestRule.setContent {
            MmoTheme {
                GdsDateWheelPicker(
                    value = external,
                    onValueChange = {
                        external = it
                        latest = it
                    },
                    yearRange = 2025..2027,
                    testTags = tags,
                )
            }
        }

        external = LocalDate.of(2026, 1, 20)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("day").performTouchInput { click(Offset(centerX, height * 5f / 6f)) }
        composeTestRule.waitForIdle()

        assertEquals(LocalDate.of(2026, 1, 21), latest)
    }

    @Test
    fun theDayWheelStaysInSyncAfterClampingFromJanuaryThirtyFirstToFebruary() {
        setContent(LocalDate.of(2026, 1, 31))

        setWheel("month", 2)
        composeTestRule.onNodeWithTag("day").performTouchInput { click(Offset(centerX, height * 5f / 6f)) }
        composeTestRule.waitForIdle()
        assertEquals(LocalDate.of(2026, 2, 28), latest)

        composeTestRule.onNodeWithTag("day").performTouchInput { click(Offset(centerX, height / 6f)) }
        composeTestRule.waitForIdle()
        assertEquals(LocalDate.of(2026, 2, 27), latest)
    }

    @Test
    fun atDoubleFontScaleEveryWheelFitsOnScreenWithoutClipping() {
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                MmoTheme {
                    GdsDateWheelPicker(
                        value = LocalDate.of(2026, 2, 28),
                        onValueChange = {},
                        yearRange = 2025..2027,
                        testTags = tags,
                    )
                }
            }
        }

        val rootWidth = composeTestRule.onRoot().getUnclippedBoundsInRoot().right
        listOf("month", "day", "year").forEach { tag ->
            val bounds = composeTestRule.onNodeWithTag(tag).assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue("$tag overflows the screen", bounds.right <= rootWidth)
        }
    }

    private fun hasDescriptionAndState(
        description: String,
        state: String,
    ) = SemanticsMatcher("$description = $state") { node ->
        node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }.contains(description) &&
            node.config.getOrElse(SemanticsProperties.StateDescription) { "" } == state
    }
}
