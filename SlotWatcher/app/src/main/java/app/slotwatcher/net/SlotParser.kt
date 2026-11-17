package app.slotwatcher.net

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class Slot(
    val date: LocalDate?,
    val start: String, // "18:30"
    val end: String,   // "18:40"
    val status: String,
) {
    val booked: Boolean get() = SlotParser.isBookedStatus(status)
    val label: String get() = "$start - $end"
}

data class ParseResult(
    /** The "Booked Slots" counter from the At a Glance card, if found. */
    val bookedCounter: Int?,
    /** Rows of the Pre-Assessments Program table for the chosen date. */
    val slots: List<Slot>,
    /** The date the page's date box showed (dd-MM-yyyy), if found. */
    val shownDate: String?,
) {
    val bookedSlots: List<Slot> get() = slots.filter { it.booked }

    val recognized: Boolean get() = bookedCounter != null || slots.isNotEmpty()

    /** Rows are the source of truth when the table was read; the counter is the fallback. */
    val totalBooked: Int get() = if (slots.isNotEmpty()) bookedSlots.size else (bookedCounter ?: 0)
}

/**
 * Reads the Pre Assessment List page.
 *
 * Written from screenshots of the page, not its source, so it looks for the content
 * (a "Booked Slots" counter, table rows with an "HH:mm - HH:mm" time range and a status)
 * rather than for exact CSS classes.
 */
object SlotParser {

    private val TIME_RANGE = Regex("""(\d{1,2}:\d{2})\s*[-–—]\s*(\d{1,2}:\d{2})""")
    private val ROW_DATE = Regex(
        """(\d{1,2})\s+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*,?\s+(\d{4})""",
        RegexOption.IGNORE_CASE,
    )
    private val DMY = Regex("""\d{2}-\d{2}-\d{4}""")
    val DMY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.US)

    /**
     * Statuses that need no action. Past sessions (completed/present/absent) are left out so a
     * finished session doesn't keep ringing for the rest of the evening.
     */
    private val NOT_BOOKED = setOf("available", "expired", "completed", "present", "absent")

    fun isBookedStatus(status: String): Boolean {
        val s = status.trim().lowercase(Locale.US)
        if (s.none { it.isLetter() }) return false // "", "--"
        return s !in NOT_BOOKED
    }

    fun parse(doc: Document, date: LocalDate): ParseResult {
        val counter = findCounter(doc, "Booked Slots")
        val slots = readRows(doc)
            .filter { it.date == null || it.date == date }
            .distinctBy { Triple(it.date, it.start, it.end) }
        return ParseResult(counter, slots, findDateInput(doc)?.attr("value")?.trim())
    }

    /** The date box in "At a Glance": an input whose value looks like 18-11-2026. */
    fun findDateInput(doc: Document): Element? =
        doc.select("input").firstOrNull { DMY.matches(it.attr("value").trim()) }

    private fun readRows(doc: Document): List<Slot> {
        val out = mutableListOf<Slot>()
        for (table in doc.select("table")) {
            val headers = table.select("thead th, thead td").map { it.text() }
                .ifEmpty { table.selectFirst("tr")?.select("th")?.map { it.text() } ?: emptyList() }
            val statusIdx = headers.indexOfFirst { it.contains("status", ignoreCase = true) }
            val timeIdx = headers.indexOfFirst {
                it.contains("time", ignoreCase = true) || it.contains("date", ignoreCase = true)
            }
            val rows = table.select("tbody > tr").ifEmpty { table.select("tr") }
            for (tr in rows) {
                val cells = tr.select("> td")
                if (cells.isEmpty()) continue
                val timeText = if (timeIdx in cells.indices) cells[timeIdx].text() else tr.text()
                val range = TIME_RANGE.find(timeText) ?: TIME_RANGE.find(tr.text()) ?: continue
                val status = if (statusIdx in cells.indices) {
                    cells[statusIdx].text()
                } else {
                    tr.selectFirst(".badge, [class*=badge], .label")?.text().orEmpty()
                }
                out += Slot(
                    date = ROW_DATE.find(timeText)?.let(::parseRowDate),
                    start = normalize(range.groupValues[1]),
                    end = normalize(range.groupValues[2]),
                    status = status.trim(),
                )
            }
        }
        return out
    }

    private fun findCounter(doc: Document, label: String): Int? {
        val el = doc.getElementsContainingOwnText(label).firstOrNull() ?: return null
        var p: Element? = el
        repeat(4) {
            p = p?.parent() ?: return null
            numberNear(p!!.text(), label)?.let { return it }
        }
        return null
    }

    private fun numberNear(text: String, label: String): Int? {
        val i = text.indexOf(label, ignoreCase = true)
        if (i < 0) return null
        Regex("""^\s*(\d+)""").find(text.substring(i + label.length))?.let { return it.groupValues[1].toInt() }
        Regex("""(\d+)\s*$""").find(text.substring(0, i))?.let { return it.groupValues[1].toInt() }
        return null
    }

    private fun parseRowDate(m: MatchResult): LocalDate? = runCatching {
        val (d, mon, y) = m.destructured
        val month = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
            .indexOf(mon.lowercase(Locale.US).take(3)) + 1
        LocalDate.of(y.toInt(), month, d.toInt())
    }.getOrNull()

    /** "9:05" -> "09:05" so times compare and sort cleanly. */
    private fun normalize(hhmm: String): String {
        val (h, m) = hhmm.split(":")
        return "%02d:%s".format(Locale.US, h.toInt(), m)
    }
}
