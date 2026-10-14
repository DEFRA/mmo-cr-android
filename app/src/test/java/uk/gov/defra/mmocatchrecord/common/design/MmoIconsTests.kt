package uk.gov.defra.mmocatchrecord.common.design

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Renders every custom [androidx.compose.foundation.Canvas]-drawn icon in `MmoIcons.kt` and forces
 * Robolectric to actually run the `Canvas` draw lambda via `captureToImage()` (semantics-only assertions
 * like `assertIsDisplayed` do not) - see `MapCanvasTests` for the established rationale/pattern.
 */
@RunWith(RobolectricTestRunner::class)
class MmoIconsTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val tag = "icon_under_test"

    @Test
    fun `CustomPlayArrowIcon draws without error`() {
        composeTestRule.setContent {
            CustomPlayArrowIcon(tint = Color.Black, modifier = Modifier.size(24.dp).testTag(tag))
        }
        composeTestRule.onNodeWithTag(tag).captureToImage()
    }

    @Test
    fun `CustomArrowForwardIcon draws without error`() {
        composeTestRule.setContent {
            CustomArrowForwardIcon(tint = Color.Black, modifier = Modifier.size(24.dp).testTag(tag))
        }
        composeTestRule.onNodeWithTag(tag).captureToImage()
    }

    @Test
    fun `CustomHomeIcon draws without error`() {
        composeTestRule.setContent {
            CustomHomeIcon(tint = Color.Black, modifier = Modifier.size(24.dp).testTag(tag))
        }
        composeTestRule.onNodeWithTag(tag).captureToImage()
    }

    @Test
    fun `CustomNotificationsIcon draws without error`() {
        composeTestRule.setContent {
            CustomNotificationsIcon(tint = Color.Black, modifier = Modifier.size(24.dp).testTag(tag))
        }
        composeTestRule.onNodeWithTag(tag).captureToImage()
    }

    @Test
    fun `CustomSettingsIcon draws without error`() {
        composeTestRule.setContent {
            CustomSettingsIcon(tint = Color.Black, modifier = Modifier.size(24.dp).testTag(tag))
        }
        composeTestRule.onNodeWithTag(tag).captureToImage()
    }

    @Test
    fun `CustomCheckCircleIcon draws without error`() {
        composeTestRule.setContent {
            CustomCheckCircleIcon(tint = Color.Black, modifier = Modifier.size(24.dp).testTag(tag))
        }
        composeTestRule.onNodeWithTag(tag).captureToImage()
    }

    @Test
    fun `CustomInfoCircleIcon draws without error`() {
        composeTestRule.setContent {
            CustomInfoCircleIcon(tint = Color.Black, modifier = Modifier.size(24.dp).testTag(tag))
        }
        composeTestRule.onNodeWithTag(tag).captureToImage()
    }

    @Test
    fun `CustomWarningIcon draws without error`() {
        composeTestRule.setContent {
            CustomWarningIcon(tint = Color.Black, modifier = Modifier.size(24.dp).testTag(tag))
        }
        composeTestRule.onNodeWithTag(tag).captureToImage()
    }

    @Test
    fun `GdsCrownIcon draws without error`() {
        composeTestRule.setContent {
            GdsCrownIcon(modifier = Modifier.testTag(tag))
        }
        composeTestRule.onNodeWithTag(tag).captureToImage()
    }

    @Test
    fun `CrownLogo renders its image`() {
        composeTestRule.setContent {
            CrownLogo(modifier = Modifier.testTag(tag))
        }
        composeTestRule.onNodeWithTag(tag).captureToImage()
    }

    @Test
    fun `RoyalCrestPlaceholder renders its image`() {
        composeTestRule.setContent {
            RoyalCrestPlaceholder(modifier = Modifier.testTag(tag))
        }
        composeTestRule.onNodeWithTag(tag).captureToImage()
    }

    /** Every icon above is called with an explicit `modifier` elsewhere in this file - this exercises
     * each composable's default `Modifier` parameter value, which only the no-argument call site hits. */
    @Test
    fun `every icon renders using its default modifier`() {
        composeTestRule.setContent {
            androidx.compose.foundation.layout.Column(modifier = Modifier.testTag(tag)) {
                CustomPlayArrowIcon(tint = Color.Black)
                CustomArrowForwardIcon(tint = Color.Black)
                CustomHomeIcon(tint = Color.Black)
                CustomNotificationsIcon(tint = Color.Black)
                CustomSettingsIcon(tint = Color.Black)
                CustomCheckCircleIcon(tint = Color.Black)
                CustomInfoCircleIcon(tint = Color.Black)
                CustomWarningIcon(tint = Color.Black)
                GdsCrownIcon()
                CrownLogo()
                RoyalCrestPlaceholder()
            }
        }
        composeTestRule.onNodeWithTag(tag).captureToImage()
    }
}
