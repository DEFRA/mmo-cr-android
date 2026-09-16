package uk.gov.defra.mmocatchrecord.feature.catchrecord.presentation.wizard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.gov.defra.mmocatchrecord.feature.catchrecord.domain.draft.DmyDate
import java.time.Instant

class LateSubmissionSupportTests {
    private val returnDate = DmyDate(day = 1, month = 1, year = 2020)

    // Return date treated as ending 2020-01-01T23:59:00Z (see LateSubmissionSupport's documented assumption).
    private fun epochMillisAt(isoInstant: String): Long = Instant.parse(isoInstant).toEpochMilli()

    @Test
    fun `null return date is never a late submission`() {
        assertFalse(LateSubmissionSupport.isLateSubmission(null, epochMillisAt("2020-06-01T00:00:00Z")))
    }

    @Test
    fun `exactly 24 hours after the return-day deadline is not yet late`() {
        // Deadline is 2020-01-01T23:59:00Z; exactly +24h is 2020-01-02T23:59:00Z.
        val exactlyTwentyFourHoursLater = epochMillisAt("2020-01-02T23:59:00Z")
        assertFalse(LateSubmissionSupport.isLateSubmission(returnDate, exactlyTwentyFourHoursLater))
    }

    @Test
    fun `one hour past the 24-hour boundary is late`() {
        val justOverTwentyFourHours = epochMillisAt("2020-01-03T00:59:00Z")
        assertTrue(LateSubmissionSupport.isLateSubmission(returnDate, justOverTwentyFourHours))
    }

    @Test
    fun `one minute before the deadline is not late`() {
        val beforeDeadline = epochMillisAt("2020-01-01T23:58:00Z")
        assertFalse(LateSubmissionSupport.isLateSubmission(returnDate, beforeDeadline))
    }

    @Test
    fun `well within the submission window is not late`() {
        val sameDay = epochMillisAt("2020-01-01T12:00:00Z")
        assertFalse(LateSubmissionSupport.isLateSubmission(returnDate, sameDay))
    }

    @Test
    fun `days since return is zero on the return date itself`() {
        assertEquals(0, LateSubmissionSupport.daysSinceReturn(returnDate, epochMillisAt("2020-01-01T12:00:00Z")))
    }

    @Test
    fun `days since return is a whole calendar-day difference, not an hours-24 division`() {
        // Only ~13 hours after the return date started, but it is already the next calendar day.
        assertEquals(1, LateSubmissionSupport.daysSinceReturn(returnDate, epochMillisAt("2020-01-02T13:00:00Z")))
    }

    @Test
    fun `days since return several days later`() {
        assertEquals(5, LateSubmissionSupport.daysSinceReturn(returnDate, epochMillisAt("2020-01-06T00:00:00Z")))
    }

    @Test
    fun `days since return is clamped to zero, never negative`() {
        val beforeReturnDate = epochMillisAt("2019-12-31T00:00:00Z")
        assertEquals(0, LateSubmissionSupport.daysSinceReturn(returnDate, beforeReturnDate))
    }
}
