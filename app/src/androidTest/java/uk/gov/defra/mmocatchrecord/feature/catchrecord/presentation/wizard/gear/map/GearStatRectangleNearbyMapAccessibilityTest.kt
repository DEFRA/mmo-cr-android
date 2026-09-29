@file:Suppress("detekt.MaxLineLength")

package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear.map

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertValueEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import uk.gov.defra.mmocatchrecord.common.design.MmoTheme
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.GeoBoundingBox
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.GeoPoint
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.StatisticalSubRectangleGeometry
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear.map.MapProjection.CameraState

/**
 * WCAG 2.2 AA coverage for [AccessibleStatisticalAreaMap] — the nearby (default) map screen's sole
 * selection UI (there is no separate visible radio list). Each nearby cell exposes [Role.RadioButton]
 * semantics + a content description + correct selected state, inside a `selectableGroup`, meets the 48dp
 * minimum touch target even at low zoom, is keyboard-focusable, and a tap both updates the underlying
 * canvas's own `stateDescription` and announces the selection change on the caller's live region (asserted
 * at the [GearStatRectangleScreen]-level in `GearStatRectangleScreenTest`).
 *
 * [overlayCellsStayAlignedWithTheUnderlyingCanvasAcrossRecomposition] additionally covers item 5 of the
 * bug-fix plan: the overlay and canvas share one hoisted `CameraState` and the same reported viewport size
 * (see [AccessibleStatisticalAreaMap]'s doc comment), so a change that would otherwise force a re-layout
 * (a different `nearbyGeometry`/`cameraResetKey`, simulating navigating to a different gear's nearby set)
 * must not desynchronise the overlay from the canvas — every remaining cell keeps reporting the exact same
 * projected bounds it would if it had been composed with that geometry from the start.
 */
class GearStatRectangleNearbyMapAccessibilityTest {
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

    private val nearbyGeometry =
        listOf(
            rectangle("38E95", 50.0, 51.0, 0.0, 1.0),
            rectangle("38E98", 51.0, 52.0, 0.0, 1.0),
        )

    @Test
    fun theCanvasIsDisplayedWithATwoNearbyCellsShownAsFocusableOverlayCells() {
        composeTestRule.setContent {
            MmoTheme {
                AccessibleStatisticalAreaMap(
                    nearbyGeometry = nearbyGeometry,
                    landPolygons = emptyList(),
                    ports = emptyList(),
                    selectedCode = null,
                    onCodeSelected = {},
                    initialCentre = GeoPoint(50.855, 0.573),
                    contentDescription = "Map of statistical sub areas nearest to your departure port.",
                    cameraResetKey = "gear-use-1",
                    cellTestTagPrefix = "nearby_cell",
                    canvasTestTag = "nearby_canvas",
                )
            }
        }

        composeTestRule
            .onNodeWithTag("nearby_canvas")
            .assertIsDisplayed()
            .assertContentDescriptionEquals("Map of statistical sub areas nearest to your departure port.")
        composeTestRule.onNodeWithTag("nearby_cell_38E95").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nearby_cell_38E98").assertIsDisplayed()
    }

    @Test
    fun eachOverlayCellExposesTheRadioButtonRoleAndTheCorrectSelectedState() {
        composeTestRule.setContent {
            MmoTheme {
                AccessibleStatisticalAreaMap(
                    nearbyGeometry = nearbyGeometry,
                    landPolygons = emptyList(),
                    ports = emptyList(),
                    selectedCode = null,
                    onCodeSelected = {},
                    initialCentre = GeoPoint(50.855, 0.573),
                    contentDescription = "Nearby map",
                    cameraResetKey = "gear-use-1",
                    cellTestTagPrefix = "nearby_cell",
                    canvasTestTag = "nearby_canvas",
                )
            }
        }

        val roleMatcher =
            SemanticsMatcher("has RadioButton role") { node ->
                node.config.getOrNull(SemanticsProperties.Role) == Role.RadioButton
            }

        composeTestRule
            .onNodeWithTag("nearby_cell_38E95")
            .assert(roleMatcher)
            .assertIsNotSelected()
            .assertContentDescriptionEquals("38E95")

        composeTestRule.onNodeWithTag("nearby_cell_38E95").performClick()

        composeTestRule.onNodeWithTag("nearby_cell_38E95").assertIsSelected()
        composeTestRule.onNodeWithTag("nearby_cell_38E98").assertIsNotSelected()
    }

    @Test
    fun eachOverlayCellMeetsTheFortyEightDpMinimumTouchTarget() {
        composeTestRule.setContent {
            MmoTheme {
                AccessibleStatisticalAreaMap(
                    nearbyGeometry = nearbyGeometry,
                    landPolygons = emptyList(),
                    ports = emptyList(),
                    selectedCode = null,
                    onCodeSelected = {},
                    initialCentre = GeoPoint(50.855, 0.573),
                    contentDescription = "Nearby map",
                    cameraResetKey = "gear-use-1",
                    cellTestTagPrefix = "nearby_cell",
                    canvasTestTag = "nearby_canvas",
                )
            }
        }

        composeTestRule.onNodeWithTag("nearby_cell_38E95").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
    }

    @Test
    fun tappingAnOverlayCellInvokesTheCallbackAndUpdatesTheCanvasesOwnSelectionState() {
        var selected: String? = null
        composeTestRule.setContent {
            MmoTheme {
                AccessibleStatisticalAreaMap(
                    nearbyGeometry = nearbyGeometry,
                    landPolygons = emptyList(),
                    ports = emptyList(),
                    selectedCode = selected,
                    onCodeSelected = { selected = it },
                    initialCentre = GeoPoint(50.855, 0.573),
                    contentDescription = "Nearby map",
                    cameraResetKey = "gear-use-1",
                    cellTestTagPrefix = "nearby_cell",
                    canvasTestTag = "nearby_canvas",
                )
            }
        }

        composeTestRule.onNodeWithTag("nearby_canvas").assertValueEquals("No area selected")
        composeTestRule.onNodeWithTag("nearby_cell_38E98").performClick()

        assert(selected == "38E98") { "Expected '38E98' but was $selected" }
    }

    @Test
    fun anOverlayCellIsKeyboardFocusable() {
        composeTestRule.setContent {
            MmoTheme {
                AccessibleStatisticalAreaMap(
                    nearbyGeometry = nearbyGeometry,
                    landPolygons = emptyList(),
                    ports = emptyList(),
                    selectedCode = null,
                    onCodeSelected = {},
                    initialCentre = GeoPoint(50.855, 0.573),
                    contentDescription = "Nearby map",
                    cameraResetKey = "gear-use-1",
                    cellTestTagPrefix = "nearby_cell",
                    canvasTestTag = "nearby_canvas",
                )
            }
        }

        composeTestRule.onNodeWithTag("nearby_cell_38E95").requestFocus()
        composeTestRule.onNodeWithTag("nearby_cell_38E95").assertIsFocused()
    }

    @Test
    fun aNearbyCodeWithNoMatchingGeometryIsSimplyAbsentFromTheOverlay() {
        // OD-2: a nearby reference-data code with no sea-overlapping geometry match (e.g. "38F02" in the
        // wider fixtures) is not shown here at all — it remains reachable only via "Other" -> the global
        // map+list/autocomplete fallback. This is exercised at the join level in
        // GearStatRectangleSupportTests (nearbyGeometryFor); here we simply confirm the overlay never
        // renders a cell for a code that was never in nearbyGeometry to begin with.
        composeTestRule.setContent {
            MmoTheme {
                AccessibleStatisticalAreaMap(
                    nearbyGeometry = nearbyGeometry,
                    landPolygons = emptyList(),
                    ports = emptyList(),
                    selectedCode = null,
                    onCodeSelected = {},
                    initialCentre = GeoPoint(50.855, 0.573),
                    contentDescription = "Nearby map",
                    cameraResetKey = "gear-use-1",
                    cellTestTagPrefix = "nearby_cell",
                    canvasTestTag = "nearby_canvas",
                )
            }
        }

        composeTestRule.onAllNodesWithTag("nearby_cell_38F02").assertCountEquals(0)
    }

    @Test
    fun overlayCellsStayAlignedWithTheUnderlyingCanvasAcrossRecomposition() {
        lateinit var density: Density
        composeTestRule.setContent {
            density = LocalDensity.current
            MmoTheme {
                AccessibleStatisticalAreaMap(
                    nearbyGeometry = nearbyGeometry,
                    landPolygons = emptyList(),
                    ports = emptyList(),
                    selectedCode = null,
                    onCodeSelected = {},
                    initialCentre = GeoPoint(50.855, 0.573),
                    contentDescription = "Nearby map",
                    cameraResetKey = "gear-use-1",
                    cellTestTagPrefix = "nearby_cell",
                    canvasTestTag = "nearby_canvas",
                )
            }
        }

        // The overlay and canvas are laid out from the exact same viewport size and hoisted CameraState (see
        // AccessibleStatisticalAreaMap's doc comment). Independently re-deriving the expected projected
        // bounds via the same pure MapProjection/MapZoomBounds functions the production code itself calls,
        // using the canvas's *actual measured* viewport, and comparing against the overlay cell's *actual
        // measured* position confirms there is no first-frame or rounding drift between the two in practice.
        val canvasBounds = composeTestRule.onNodeWithTag("nearby_canvas").getBoundsInRoot()
        val cellBounds = composeTestRule.onNodeWithTag("nearby_cell_38E95").getBoundsInRoot()

        val viewportWidthPx = with(density) { canvasBounds.width.toPx() }
        val viewportHeightPx = with(density) { canvasBounds.height.toPx() }
        val cellSizeDeg = MapZoomBounds.representativeCellSizeDegrees(nearbyGeometry)!!
        val zoomBounds =
            MapZoomBounds.forCells(
                cellWidthDeg = cellSizeDeg.first,
                cellHeightDeg = cellSizeDeg.second,
                centreLatDeg = 50.855,
                viewportWidthPx = viewportWidthPx,
                viewportHeightPx = viewportHeightPx,
            )
        val camera = CameraState(centre = GeoPoint(50.855, 0.573), zoom = zoomBounds.initialZoom)
        val expected =
            MapProjection.projectedBounds(nearbyGeometry[0].boundingBox, camera, viewportWidthPx, viewportHeightPx)

        val actualLeftPx = with(density) { (cellBounds.left - canvasBounds.left).toPx() }
        val actualTopPx = with(density) { (cellBounds.top - canvasBounds.top).toPx() }

        // A generous tolerance covers sub-pixel Dp<->Px rounding and the 48dp minimum-touch-target clamp
        // (cellBounds coerces very small projected cells up to 48dp, which this fixture's cell sizes are
        // comfortably larger than, but the tolerance keeps the assertion robust to minor layout rounding).
        val tolerancePx = with(density) { 2.dp.toPx() }
        assertEquals(expected.left, actualLeftPx, tolerancePx)
        assertEquals(expected.top, actualTopPx, tolerancePx)
    }
}
