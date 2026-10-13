package uk.gov.defra.mmocatchrecord.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Covers every [BiometryKind], [BiometricAvailability] and [BiometricError] variant. */
class BiometricTypesTests {
    @Test
    fun `BiometryKind has the four expected entries`() {
        assertEquals(
            listOf("FINGERPRINT", "FACE", "IRIS", "DEVICE_CREDENTIAL"),
            BiometryKind.entries.map { it.name },
        )
        assertEquals(BiometryKind.FINGERPRINT, BiometryKind.valueOf("FINGERPRINT"))
    }

    @Test
    fun `BiometricAvailability data objects are each distinct singletons`() {
        val variants: List<BiometricAvailability> =
            listOf(
                BiometricAvailability.Available,
                BiometricAvailability.NoHardware,
                BiometricAvailability.HardwareUnavailable,
                BiometricAvailability.NoneEnrolled,
                BiometricAvailability.SecurityUpdateRequired,
            )

        assertEquals(variants.size, variants.toSet().size)
    }

    @Test
    fun `BiometricAvailability Unknown carries its sanitised reason and supports equality`() {
        val first = BiometricAvailability.Unknown("sensor timeout")
        val second = BiometricAvailability.Unknown("sensor timeout")
        val different = BiometricAvailability.Unknown("other reason")

        assertEquals("sensor timeout", first.reason)
        assertEquals(first, second)
        assertNotEquals(first, different)
    }

    @Test
    fun `BiometricError data objects are each distinct singletons`() {
        val variants: List<BiometricError> =
            listOf(
                BiometricError.UserCancelled,
                BiometricError.LockedOutTemporarily,
                BiometricError.LockedOutPermanently,
                BiometricError.NotEnrolled,
            )

        assertEquals(variants.size, variants.toSet().size)
    }

    @Test
    fun `BiometricError Unknown carries its sanitised reason and supports equality`() {
        val first = BiometricError.Unknown("platform code 7")
        val second = BiometricError.Unknown("platform code 7")
        val different = BiometricError.Unknown("platform code 9")

        assertEquals("platform code 7", first.reason)
        assertEquals(first, second)
        assertNotEquals(first, different)
    }
}
