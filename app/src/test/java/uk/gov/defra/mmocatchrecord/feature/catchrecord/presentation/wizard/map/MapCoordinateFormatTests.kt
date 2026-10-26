package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapCoordinateFormatTests {
    @Test
    fun `magnitude formats a positive value to two decimal places`() {
        assertEquals("50.86", MapCoordinateFormat.magnitude(50.86))
    }

    @Test
    fun `magnitude formats a negative value as its absolute magnitude`() {
        assertEquals("0.37", MapCoordinateFormat.magnitude(-0.37))
    }

    @Test
    fun `magnitude rounds to two decimal places`() {
        assertEquals("50.87", MapCoordinateFormat.magnitude(50.8651))
    }

    @Test
    fun `magnitude formats zero without a sign`() {
        assertEquals("0.00", MapCoordinateFormat.magnitude(0.0))
    }

    @Test
    fun `isNorth is true for a non-negative latitude`() {
        assertTrue(MapCoordinateFormat.isNorth(50.86))
        assertTrue(MapCoordinateFormat.isNorth(0.0))
    }

    @Test
    fun `isNorth is false for a negative latitude`() {
        assertFalse(MapCoordinateFormat.isNorth(-10.0))
    }

    @Test
    fun `isEast is true for a non-negative longitude`() {
        assertTrue(MapCoordinateFormat.isEast(0.37))
        assertTrue(MapCoordinateFormat.isEast(0.0))
    }

    @Test
    fun `isEast is false for a negative longitude`() {
        assertFalse(MapCoordinateFormat.isEast(-1.5))
    }
}
