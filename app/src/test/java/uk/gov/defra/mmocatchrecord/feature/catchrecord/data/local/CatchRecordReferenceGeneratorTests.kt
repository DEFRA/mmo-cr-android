package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime

class CatchRecordReferenceGeneratorTests {
    private fun epochMillis(ukLocal: LocalDateTime): Long =
        ukLocal.atZone(CatchRecordReferenceGenerator.UK_ZONE).toInstant().toEpochMilli()

    private fun utcMillis(utc: String): Long = Instant.parse(utc).toEpochMilli()

    @Test
    fun `generates RSS_NUMBER-DDMMYYYY-HHMMSS`() {
        val millis = epochMillis(LocalDateTime.of(2026, 9, 20, 11, 22, 33))

        assertEquals("B14974-20092026-112233", CatchRecordReferenceGenerator.generate("B14974", millis))
    }

    @Test
    fun `zero-pads single-digit date and time parts`() {
        val millis = epochMillis(LocalDateTime.of(2026, 1, 2, 3, 4, 5))

        assertEquals("B14974-02012026-030405", CatchRecordReferenceGenerator.generate("B14974", millis))
    }

    @Test
    fun `uses UK summer time (BST) so a late UTC evening rolls into the next UK day`() {
        assertEquals(
            "B14974-01082026-003000",
            CatchRecordReferenceGenerator.generate("B14974", utcMillis("2026-07-31T23:30:00Z")),
        )
    }

    @Test
    fun `uses GMT in winter, matching UTC`() {
        assertEquals(
            "B14974-15012026-233000",
            CatchRecordReferenceGenerator.generate("B14974", utcMillis("2026-01-15T23:30:00Z")),
        )
    }

    @Test
    fun `normalises the registration number to upper-case alphanumerics`() {
        val millis = epochMillis(LocalDateTime.of(2026, 9, 20, 11, 22, 33))

        assertEquals("B14974-20092026-112233", CatchRecordReferenceGenerator.generate(" b-14 974 ", millis))
    }

    @Test
    fun `normalising a missing or symbol-only RSS number gives null`() {
        assertNull(CatchRecordReferenceGenerator.normaliseRssNumber(null))
        assertNull(CatchRecordReferenceGenerator.normaliseRssNumber("  -- "))
        assertEquals("PH101", CatchRecordReferenceGenerator.normaliseRssNumber(" ph-101 "))
    }

    @Test
    fun `generate rejects an RSS number with no usable characters`() {
        assertThrows(IllegalArgumentException::class.java) {
            CatchRecordReferenceGenerator.generate(" - ", 0L)
        }
    }

    @Test
    fun `the repeated BST to GMT hour renders the same local timestamp twice`() {
        val firstPass = CatchRecordReferenceGenerator.generate("PH101", utcMillis("2026-10-25T00:30:00Z"))
        val secondPass = CatchRecordReferenceGenerator.generate("PH101", utcMillis("2026-10-25T01:30:00Z"))

        // Why the DAO must enforce uniqueness rather than relying on the clock.
        assertEquals("PH101-25102026-013000", firstPass)
        assertEquals(firstPass, secondPass)
    }

    @Test
    fun `nextAfter moves the reference on one second, rolling over minutes, days and years`() {
        assertEquals("PH101-20092026-112234", CatchRecordReferenceGenerator.nextAfter("PH101-20092026-112233"))
        assertEquals("PH101-01012027-000000", CatchRecordReferenceGenerator.nextAfter("PH101-31122026-235959"))
    }

    @Test
    fun `nextAfter rejects anything that is not a Trip ID`() {
        listOf("A1234520260727150815", "PH101-32132026-112233", "ph101-20092026-112233").forEach { reference ->
            assertThrows(IllegalArgumentException::class.java) { CatchRecordReferenceGenerator.nextAfter(reference) }
        }
    }
}
