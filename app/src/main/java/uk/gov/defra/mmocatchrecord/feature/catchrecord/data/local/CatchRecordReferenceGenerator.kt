package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Generates the Trip ID (`GBR-{PLN}-ddMMyy-HHmmss`) once, when a catch record is started; it then stays
 * with the draft through submission and sync for traceability. See ADR 0022 (format, OQ-4).
 */
object CatchRecordReferenceGenerator {
    // UK local time (GMT/BST) regardless of the device's own time zone.
    val UK_ZONE: ZoneId = ZoneId.of("Europe/London")
    private const val PREFIX = "GBR"
    private val TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("ddMMyy-HHmmss")
    private val NON_ALPHANUMERIC = Regex("[^A-Za-z0-9]")
    private val REFERENCE_PATTERN = Regex("^GBR-([A-Z0-9]+)-(\\d{6}-\\d{6})$")

    /** Upper-case alphanumerics only, or `null` when nothing usable remains. */
    fun normaliseRssNumber(raw: String?): String? =
        raw
            ?.replace(NON_ALPHANUMERIC, "")
            ?.uppercase()
            ?.takeIf { it.isNotEmpty() }

    fun generate(
        rssNumber: String,
        epochMillis: Long,
    ): String {
        val pln = requireNotNull(normaliseRssNumber(rssNumber)) { "RSS number has no alphanumeric characters" }
        val timestamp = Instant.ofEpochMilli(epochMillis).atZone(UK_ZONE).format(TIMESTAMP_FORMAT)
        return "$PREFIX-$pln-$timestamp"
    }

    /** The same reference one wall-clock second later; used to resolve a Trip ID collision. */
    fun nextAfter(reference: String): String {
        val match = requireNotNull(REFERENCE_PATTERN.matchEntire(reference)) { "Not a Trip ID reference" }
        val (pln, timestamp) = match.destructured
        val localTime =
            try {
                LocalDateTime.parse(timestamp, TIMESTAMP_FORMAT)
            } catch (e: DateTimeParseException) {
                throw IllegalArgumentException("Not a Trip ID reference", e)
            }
        return "$PREFIX-$pln-${localTime.plusSeconds(1).format(TIMESTAMP_FORMAT)}"
    }
}
