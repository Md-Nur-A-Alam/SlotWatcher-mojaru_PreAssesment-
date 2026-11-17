package app.slotwatcher.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object Format {
    private val TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
    private val DAY = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.US)
    private val SHORT_DAY = DateTimeFormatter.ofPattern("d MMM", Locale.US)
    private val STAMP = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.US)

    fun time(t: LocalTime): String = t.format(TIME)
    fun time(t: LocalDateTime): String = t.format(TIME)
    fun day(d: LocalDate): String = d.format(DAY)
    fun shortDay(d: LocalDate): String = d.format(SHORT_DAY)
    fun stamp(t: LocalDateTime): String = t.format(STAMP)

    /** "18:30" -> "6:30 PM" */
    fun time(hhmm: String): String = runCatching { time(LocalTime.parse(hhmm)) }.getOrDefault(hhmm)

    /** "a", "a and b", "a, b and c" */
    fun joinAnd(items: List<String>): String = when (items.size) {
        0 -> ""
        1 -> items[0]
        else -> items.dropLast(1).joinToString(", ") + " and " + items.last()
    }

    fun slotsBooked(n: Int): String = if (n == 1) "1 slot booked" else "$n slots booked"

    /** What the alarm says: "2 slots booked: 6:30 PM and 7:10 PM." */
    fun spokenMessage(n: Int, startTimes: List<String>): String {
        val head = slotsBooked(n)
        return if (startTimes.isEmpty()) "$head." else "$head: ${joinAnd(startTimes.map(::time))}."
    }
}
