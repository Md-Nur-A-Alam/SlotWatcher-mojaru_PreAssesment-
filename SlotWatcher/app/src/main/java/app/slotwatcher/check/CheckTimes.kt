package app.slotwatcher.check

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** What the user enters on the New Session screen. */
data class WatchSession(
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val gapMinutes: Int,
)

object CheckTimes {

    const val MIN_GAP = 1
    const val MAX_GAP = 180

    /**
     * Every check time from start to end, one gap apart (FR-5, FR-6).
     * 6:25 PM to 9:15 PM with a 10-minute gap gives 6:25, 6:35 ... 9:05, 9:15 (18 checks).
     * If the end isn't on a gap step, the last check is the last step before it.
     */
    fun of(s: WatchSession): List<LocalDateTime> {
        if (s.gapMinutes < MIN_GAP || !s.end.isAfter(s.start)) return emptyList()
        val out = mutableListOf<LocalDateTime>()
        val endAt = s.date.atTime(s.end)
        var t = s.date.atTime(s.start)
        while (!t.isAfter(endAt)) {
            out += t
            t = t.plusMinutes(s.gapMinutes.toLong())
        }
        return out
    }

    /** The first check strictly after [after], or null when the session is over (FR-10). */
    fun nextAfter(s: WatchSession, after: LocalDateTime): LocalDateTime? =
        of(s).firstOrNull { it.isAfter(after) }

    /** Null when the session can start, otherwise the reason it can't (FR-7). */
    fun validate(s: WatchSession, now: LocalDateTime): String? = when {
        s.gapMinutes < MIN_GAP -> "Gap must be at least $MIN_GAP minute."
        s.gapMinutes > MAX_GAP -> "Gap can be at most $MAX_GAP minutes."
        !s.end.isAfter(s.start) -> "End time must be after start time."
        s.date.isBefore(now.toLocalDate()) -> "The date is in the past."
        nextAfter(s, now) == null -> "All check times for this window have already passed."
        else -> null
    }
}
