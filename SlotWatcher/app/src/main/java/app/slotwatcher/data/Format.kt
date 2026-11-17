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

    // ---- spoken messages per voice language ----

    fun speechBooked(lang: VoiceLanguage, n: Int, startTimes: List<String>): String = when (lang) {
        VoiceLanguage.English -> spokenMessage(n, startTimes)
        VoiceLanguage.Bangla -> {
            val head = "${bnDigits(n.toString())}টি স্লট বুক হয়েছে।"
            if (startTimes.isEmpty()) head
            else "$head সময়: ${bnJoin(startTimes.mapNotNull { runCatching { bnTime(LocalTime.parse(it)) }.getOrNull() })}।"
        }
    }

    fun speechNoneBooked(lang: VoiceLanguage, next: LocalDateTime?): String = when (lang) {
        VoiceLanguage.English ->
            "No slot booked." + (next?.let { " Next check at ${time(it)}." } ?: " That was the last check.")
        VoiceLanguage.Bangla ->
            "কোনো স্লট বুক হয়নি।" + (next?.let { " পরের চেক ${bnTime(it.toLocalTime())}।" } ?: " এটাই ছিল শেষ চেক।")
    }

    fun speechLoginFailed(lang: VoiceLanguage): String = when (lang) {
        VoiceLanguage.English -> "Login failed. Slot Watcher is paused."
        VoiceLanguage.Bangla -> "লগইন ব্যর্থ হয়েছে। স্লট ওয়াচার বন্ধ রাখা হয়েছে।"
    }

    private val BN_DIGITS = "০১২৩৪৫৬৭৮৯"

    fun bnDigits(s: String): String = s.map { if (it in '0'..'9') BN_DIGITS[it - '0'] else it }.joinToString("")

    /** 18:30 -> "সন্ধ্যা ৬টা ৩০ মিনিট" */
    fun bnTime(t: LocalTime): String {
        val period = when (t.hour) {
            in 4..5 -> "ভোর"
            in 6..11 -> "সকাল"
            in 12..14 -> "দুপুর"
            in 15..17 -> "বিকাল"
            in 18..19 -> "সন্ধ্যা"
            else -> "রাত"
        }
        val h12 = if (t.hour % 12 == 0) 12 else t.hour % 12
        val minutes = if (t.minute == 0) "" else " ${bnDigits(t.minute.toString())} মিনিট"
        return "$period ${bnDigits(h12.toString())}টা$minutes"
    }

    private fun bnJoin(items: List<String>): String = when (items.size) {
        0 -> ""
        1 -> items[0]
        else -> items.dropLast(1).joinToString(", ") + " এবং " + items.last()
    }
}
