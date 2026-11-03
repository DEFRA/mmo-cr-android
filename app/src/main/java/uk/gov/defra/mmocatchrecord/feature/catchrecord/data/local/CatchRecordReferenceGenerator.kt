package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Generates the Trip ID (`RSS_NUMBER-DDMMYYYY-HHMMSS`, e.g. `B14974-20092026-112233`) once, when a catch
 * record is started; it then stays with the draft through submission and sync for traceability.
 */
object CatchRecordReferenceGenerator {
    // UK local time (GMT/BST) regardless of the device's own time zone.
    val UK_ZONE: ZoneId = ZoneId.of("Europe/London")
    private val TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("ddMMyyyy-HHmmss")
    private val NON_ALPHANUMERIC = Regex("[^A-Za-z0-9]")
    private val REFERENCE_PATTERN = Regex("^([A-Z0-9]+)-(\\d{8}-\\d{6})$")

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
        val prefix = requireNotNull(normaliseRssNumber(rssNumber)) { "RSS number has no alphanumeric characters" }
        val timestamp = Instant.ofEpochMilli(epochMillis).atZone(UK_ZONE).format(TIMESTAMP_FORMAT)
        return "$prefix-$timestamp"
    }

    /** The same reference one wall-clock second later; used to resolve a Trip ID collision. */
    fun nextAfter(reference: String): String {
        val match = requireNotNull(REFERENCE_PATTERN.matchEntire(reference)) { "Not a Trip ID reference" }
        val (prefix, timestamp) = match.destructured
        val localTime =
            try {
                LocalDateTime.parse(timestamp, TIMESTAMP_FORMAT)
            } catch (e: DateTimeParseException) {
                throw IllegalArgumentException("Not a Trip ID reference", e)
            }
        return "$prefix-${localTime.plusSeconds(1).format(TIMESTAMP_FORMAT)}"
    }
}
