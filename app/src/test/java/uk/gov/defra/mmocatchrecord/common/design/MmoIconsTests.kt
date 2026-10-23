package uk.gov.defra.mmocatchrecord.common.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class MmoIconsTests {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val tag = "icon_under_test"
    private val tint = Color.Red

    private val tintedIcons: List<Pair<String, @Composable (Modifier) -> Unit>> =
        listOf(
            "CustomPlayArrowIcon" to { m -> CustomPlayArrowIcon(tint = tint, modifier = m) },
            "CustomArrowForwardIcon" to { m -> CustomArrowForwardIcon(tint = tint, modifier = m) },
            "CustomHomeIcon" to { m -> CustomHomeIcon(tint = tint, modifier = m) },
            "CustomNotificationsIcon" to { m -> CustomNotificationsIcon(tint = tint, modifier = m) },
            "CustomSettingsIcon" to { m -> CustomSettingsIcon(tint = tint, modifier = m) },
            "CustomCheckCircleIcon" to { m -> CustomCheckCircleIcon(tint = tint, modifier = m) },
            "CustomInfoCircleIcon" to { m -> CustomInfoCircleIcon(tint = tint, modifier = m) },
            "CustomWarningIcon" to { m -> CustomWarningIcon(tint = tint, modifier = m) },
        )

    @Test
    fun `every tinted icon draws its glyph in the supplied tint`() {
        composeTestRule.setContent {
            Column {
                tintedIcons.forEachIndexed { index, (_, icon) -> icon(Modifier.size(24.dp).testTag("$tag$index")) }
            }
        }

        tintedIcons.forEachIndexed { index, (name, _) ->
            val tinted = composeTestRule.onNodeWithTag("$tag$index").captureToImage().countOf(tint)
            assertTrue("$name drew no pixels in its tint", tinted > 0)
        }
    }

    @Test
    fun `untinted crown and crest images render visible artwork`() {
        composeTestRule.setContent {
            Column {
                GdsCrownIcon(modifier = Modifier.testTag("${tag}0"))
                CrownLogo(modifier = Modifier.testTag("${tag}1"))
                RoyalCrestPlaceholder(modifier = Modifier.testTag("${tag}2"))
            }
        }

        listOf("GdsCrownIcon", "CrownLogo", "RoyalCrestPlaceholder").forEachIndexed { index, name ->
            val colours =
                composeTestRule
                    .onNodeWithTag("$tag$index")
                    .captureToImage()
                    .pixels()
                    .toSet()
            assertTrue("$name rendered a blank image", colours.size > 1)
        }
    }

    @Test
    fun `every icon renders at its default size when no modifier is supplied`() {
        composeTestRule.setContent {
            Column(modifier = Modifier.testTag(tag)) {
                CustomPlayArrowIcon(tint = tint)
                CustomArrowForwardIcon(tint = tint)
                CustomHomeIcon(tint = tint)
                CustomNotificationsIcon(tint = tint)
                CustomSettingsIcon(tint = tint)
                CustomCheckCircleIcon(tint = tint)
                CustomInfoCircleIcon(tint = tint)
                CustomWarningIcon(tint = tint)
                GdsCrownIcon()
                CrownLogo()
                RoyalCrestPlaceholder()
            }
        }

        assertTrue(composeTestRule.onNodeWithTag(tag).captureToImage().countOf(tint) > 0)
    }
}
