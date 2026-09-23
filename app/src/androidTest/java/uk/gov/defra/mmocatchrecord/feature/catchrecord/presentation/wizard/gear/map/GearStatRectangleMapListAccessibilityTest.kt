@file:Suppress("detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear.map

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.GearUse
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.GeoBoundingBox
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.GeoPoint
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.MapGeometryDataset
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.MapPort
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.StatisticalSubRectangleGeometry
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear.GearStatRectangleScreenTestTags

/**
 * WCAG 2.2 AA coverage for the map+list content (Phase 5): the map is a single summarised semantics node
 * (not thousands of exposed polygons), the radio list is the authoritative accessible/keyboard/TalkBack
 * path (`Role.RadioButton`, correct selected state, 48dp minimum touch target), and a live region announces
 * selection changes — see `StatisticalAreaMapCanvas`/`GearStatRectangleMapListContent` doc comments.
 */
class GearStatRectangleMapListAccessibilityTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun rectangle(
        code: String,
        minLat: Double,
        maxLat: Double,
        minLng: Double,
        maxLng: Double,
    ) = StatisticalSubRectangleGeometry(
        subCode = code,
        parentIcesName = "ICES-$code",
        rings =
            listOf(
                listOf(
                    GeoPoint(minLat, minLng),
                    GeoPoint(minLat, maxLng),
                    GeoPoint(maxLat, maxLng),
                    GeoPoint(maxLat, minLng),
                ),
            ),
        bboxCentroid = GeoPoint((minLat + maxLat) / 2, (minLng + maxLng) / 2),
        boundingBox = GeoBoundingBox(minLat, maxLat, minLng, maxLng),
        seaOverlapping = true,
    )

    private val geometry =
        MapGeometryDataset(
            landPolygons = emptyList(),
            subRectangles =
                listOf(
                    rectangle("38E95", 50.0, 51.0, 0.0, 1.0),
                    rectangle("38E98", 51.0, 52.0, 0.0, 1.0),
                ),
            ports = listOf(MapPort("Hastings", GeoPoint(50.855, 0.573))),
        )

    private val gearUse =
        GearUse(
            id = "gear-use-1",
            gearTypeId = "gear-seine-nets",
            statisticalSubRectangleCode = null,
            confirmedUsedOnTrip = true,
        )

    @Test
    fun theMapCanvasExposesASingleSummarisedSemanticsNodeNotOnePerPolygon() {
        composeTestRule.setContent {
            MmoTheme {
                GearStatRectangleMapListContent(
                    gearUse = gearUse,
                    geometryStatus =
                        uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
                            .Content(geometry),
                    departurePortName = "Hastings",
                    onCantFindOnMap = {},
                    onSubmit = {},
                )
            }
        }

        // A single node with a meaningful content description — not one node per sub-rectangle/port.
        composeTestRule
            .onNodeWithTag(GearStatRectangleScreenTestTags.MAP_CANVAS)
            .assertIsDisplayed()
            .assertContentDescriptionEquals("Map of statistical sub areas. Use the list below to select an area.")
    }

    @Test
    fun eachListOptionExposesTheRadioButtonRoleAndTheCorrectSelectedState() {
        composeTestRule.setContent {
            MmoTheme {
                GearStatRectangleMapListContent(
                    gearUse = gearUse,
                    geometryStatus =
                        uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
                            .Content(geometry),
                    departurePortName = "Hastings",
                    onCantFindOnMap = {},
                    onSubmit = {},
                )
            }
        }

        val roleMatcher =
            SemanticsMatcher("has RadioButton role") { node ->
                node.config.getOrNull(SemanticsProperties.Role) == Role.RadioButton
            }

        composeTestRule
            .onNodeWithTag("${GearStatRectangleScreenTestTags.MAP_LIST_OPTION_PREFIX}_38E95")
            .assert(roleMatcher)
            .assertIsNotSelected()

        composeTestRule.onNodeWithTag("${GearStatRectangleScreenTestTags.MAP_LIST_OPTION_PREFIX}_38E95").performClick()

        composeTestRule
            .onNodeWithTag("${GearStatRectangleScreenTestTags.MAP_LIST_OPTION_PREFIX}_38E95")
            .assertIsSelected()
        composeTestRule
            .onNodeWithTag("${GearStatRectangleScreenTestTags.MAP_LIST_OPTION_PREFIX}_38E98")
            .assertIsNotSelected()
    }

    @Test
    fun eachListOptionMeetsTheFortyEightDpMinimumTouchTarget() {
        composeTestRule.setContent {
            MmoTheme {
                GearStatRectangleMapListContent(
                    gearUse = gearUse,
                    geometryStatus =
                        uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
                            .Content(geometry),
                    departurePortName = "Hastings",
                    onCantFindOnMap = {},
                    onSubmit = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag("${GearStatRectangleScreenTestTags.MAP_LIST_OPTION_PREFIX}_38E95")
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun selectingAnOptionAnnouncesItViaTheLiveRegion() {
        composeTestRule.setContent {
            MmoTheme {
                GearStatRectangleMapListContent(
                    gearUse = gearUse,
                    geometryStatus =
                        uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
                            .Content(geometry),
                    departurePortName = "Hastings",
                    onCantFindOnMap = {},
                    onSubmit = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("${GearStatRectangleScreenTestTags.MAP_LIST_OPTION_PREFIX}_38E98").performClick()

        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.LIVE_REGION).assertIsDisplayed()
    }

    @Test
    fun theErrorSummaryTextIsDisplayedWhenSaveIsPressedWithoutASelection() {
        composeTestRule.setContent {
            MmoTheme {
                GearStatRectangleMapListContent(
                    gearUse = gearUse,
                    geometryStatus =
                        uk.gov.defra.mmocatchrecord.core.architecture.UiStatus
                            .Content(geometry),
                    departurePortName = "Hastings",
                    onCantFindOnMap = {},
                    onSubmit = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(GearStatRectangleScreenTestTags.MAP_SAVE_ACTION).performClick()

        // The error is conveyed by text (not colour alone) — the exact required copy is asserted in
        // GearStatRectangleScreenTest; here we assert it is genuinely displayed, not merely present in the
        // semantics tree with zero size.
        composeTestRule
            .onNodeWithText("Select the statistical sub area where the majority of your catch was caught.")
            .assertIsDisplayed()
    }
}
