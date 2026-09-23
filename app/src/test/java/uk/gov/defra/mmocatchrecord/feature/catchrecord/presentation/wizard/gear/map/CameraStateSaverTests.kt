package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear.map

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.map.GeoPoint
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear.map.MapProjection.CameraState
import uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.gear.map.MapProjection.ScreenPoint

/**
 * Finding: "camera Saver silently narrows Double lat/lng to Float, losing precision across process
 * death/config-change restoration". Asserts the fix directly against [CameraStateSaver]: [GeoPoint.lat]/
 * [GeoPoint.lng] must round-trip through `save`/`restore` at their real [Double] precision, not merely "close
 * enough" after a lossy `Float` round-trip (a naive test using a `Float`-precision delta would pass even on
 * the old, broken implementation — this test intentionally uses a lat/lng value that is only distinguishable
 * at `Double` precision, and asserts **exact** equality, to genuinely catch a regression back to `Float`).
 */
class CameraStateSaverTests {
    private val saverScope = SaverScope { true }

    @Test
    fun restoresTheGeoPointCentreAtFullDoublePrecisionRatherThanNarrowingToFloat() {
        // Chosen so that a Float round-trip (7 significant decimal digits) would visibly corrupt it, but a
        // genuine Double round-trip (15-17 significant decimal digits) will not.
        val preciseCentre = GeoPoint(lat = 50.855123456789012, lng = 0.573987654321098)
        val original =
            CameraState(
                centre = preciseCentre,
                zoom = 2.5f,
                panOffsetPx = ScreenPoint(x = 12.5f, y = -7.25f),
            )

        val saved = with(saverScope) { CameraStateSaver.run { save(original) } }
        checkNotNull(saved) { "CameraStateSaver must be able to save every CameraState value" }
        val restored = CameraStateSaver.restore(saved)

        assertEquals(preciseCentre.lat, restored?.centre?.lat ?: Double.NaN, 0.0)
        assertEquals(preciseCentre.lng, restored?.centre?.lng ?: Double.NaN, 0.0)
        assertEquals(original.zoom, restored?.zoom ?: Float.NaN, 0.0f)
        assertEquals(original.panOffsetPx.x, restored?.panOffsetPx?.x ?: Float.NaN, 0.0f)
        assertEquals(original.panOffsetPx.y, restored?.panOffsetPx?.y ?: Float.NaN, 0.0f)
    }

    @Test
    fun aFloatRoundTripWouldHaveLostPrecisionProvingThisTestIsAGenuineRegressionGuard() {
        // Sanity-checks the test fixture itself: confirms the chosen lat/lng genuinely does lose precision
        // when narrowed to Float and back, so the assertion above is a meaningful guard against the old,
        // broken `toFloat()`/`toDouble()` implementation rather than a coincidentally-passing one.
        val precise = 50.855123456789012
        val viaFloat = precise.toFloat().toDouble()

        assertTrue("test fixture is not a genuine Double-precision value if this fails", precise != viaFloat)
    }
}
