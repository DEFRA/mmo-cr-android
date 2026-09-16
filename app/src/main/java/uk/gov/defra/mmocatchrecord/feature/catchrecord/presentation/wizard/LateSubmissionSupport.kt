package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard

import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DmyDate
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * Pure (no Android/Compose dependency) logic for the Phase 8 late-submission warning: "This catch record
 * is being submitted X days after the trip end date", shown only when the catch record is submitted more
 * than 24 hours after the trip's return date — see `WizardStep.LateSubmissionWarning` and
 * `nextWizardStepForDraft`.
 *
 * **Assumption (flagged):** [DmyDate] captures no time-of-day (only day/month/year — see its own doc
 * comment), so there is no captured "trip end time" to compare against. This treats the return date as
 * ending at 23:59 UTC for the purposes of the "submit within 24 hours" rule, i.e. a trip that returned on
 * day D is only late once `now` passes 23:59 UTC on day D+1. This is a deliberate, conservative
 * simplification (it *understates* lateness for a trip that actually ended earlier in day D) rather than
 * an invented time value; revisit if a real return-time field is ever captured.
 */
object LateSubmissionSupport {
    private const val SUBMISSION_WINDOW_HOURS = 24L
    private const val END_OF_DAY_HOUR = 23
    private const val END_OF_DAY_MINUTE = 59
    private val ZONE = ZoneOffset.UTC

    private fun endOfReturnDay(returnDate: DmyDate): Instant =
        LocalDateTime
            .of(returnDate.year, returnDate.month, returnDate.day, END_OF_DAY_HOUR, END_OF_DAY_MINUTE)
            .atZone(ZONE)
            .toInstant()

    /**
     * `true` once strictly more than [SUBMISSION_WINDOW_HOURS] have elapsed since the end of the return
     * date. `false` (never late) if [returnDate] is `null` (nothing to compare against yet) or exactly at
     * the 24-hour boundary — the rule is "submit **within** 24 hours", so exactly 24h00m is still in time.
     */
    fun isLateSubmission(
        returnDate: DmyDate?,
        nowEpochMillis: Long,
    ): Boolean {
        val deadline = returnDate?.let(::endOfReturnDay) ?: return false
        val now = Instant.ofEpochMilli(nowEpochMillis)
        return Duration.between(deadline, now).toHours() > SUBMISSION_WINDOW_HOURS
    }

    /**
     * Whole number of calendar days between [returnDate] and `now`, for the warning screen's headline
     * ("...being submitted **X** days after the trip end date"). Calendar-day difference (not an hours/24
     * division), so a return date of "yesterday" always reads as "1 day", regardless of time of day.
     * Never negative (clamped to 0), since the warning is only ever shown when [isLateSubmission] is true.
     */
    fun daysSinceReturn(
        returnDate: DmyDate,
        nowEpochMillis: Long,
    ): Int {
        val returnLocalDate = LocalDate.of(returnDate.year, returnDate.month, returnDate.day)
        val nowLocalDate = Instant.ofEpochMilli(nowEpochMillis).atZone(ZONE).toLocalDate()
        return ChronoUnit.DAYS
            .between(returnLocalDate, nowLocalDate)
            .coerceAtLeast(0L)
            .toInt()
    }
}
