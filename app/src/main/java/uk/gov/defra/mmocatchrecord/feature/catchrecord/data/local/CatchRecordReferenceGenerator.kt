package uk.gov.defra.mmocatchrecord.feature.catchrecord.data.local

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Generates the user-facing catch-record reference shown throughout the wizard and the Phase 8
 * submission-result screens (e.g. `"A1234520260727150815"`), once per draft at creation time — see
 * [RoomCatchRecordDraftRepository.startDraft].
 *
 * **Deviation (flagged):** the confirmed screenshots show this exact format (a short alphanumeric prefix
 * followed by a 14-digit `yyyyMMddHHmmss` timestamp) but do not confirm what the prefix itself encodes
 * (e.g. a vessel PLN/licence-derived code) — no reference-data field for it exists yet. This generator
 * reproduces the confirmed *shape* using a fixed placeholder prefix and a real creation-time timestamp,
 * rather than inventing a vessel-derived encoding; revisit once the real reference format is confirmed.
 */
object CatchRecordReferenceGenerator {
    private const val PLACEHOLDER_PREFIX = "A12345"
    private val TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

    fun generate(epochMillis: Long): String {
        val timestamp =
            Instant
                .ofEpochMilli(epochMillis)
                .atZone(ZoneOffset.UTC)
                .format(TIMESTAMP_FORMAT)
        return "$PLACEHOLDER_PREFIX$timestamp"
    }
}
