package app.slotwatcher.net

import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class Slot(
    val date: LocalDate?,
    val start: String, // "18:30"
    val end: String,   // "18:40"
    val status: String,
    /** The Participant Name column, when the slot has one. */
    val participant: String? = null,
) {
    val booked: Boolean get() = SlotParser.isBookedStatus(status)
    val label: String get() = "$start - $end"

    /** Stored in history as "18:40 - 18:50|Rukiya Mahmud". */
    val historyLabel: String get() = if (participant != null) "$label|$participant" else label
}

/**
 * Where the slots came from: the panel's data feed (normal), the page after its JavaScript ran,
 * or the raw server HTML (last resort).
 */
enum class ParseSource { Api, Rendered, Static }

data class ParseResult(
    /** The "Booked Slots" counter from the At a Glance card, if found. */
    val bookedCounter: Int?,
    /** Rows of the Pre-Assessments Program table for the chosen date. */
    val slots: List<Slot>,
    /** The date the page's date box showed (dd-MM-yyyy), if found. */
    val shownDate: String?,
    /** The "Total Slots" counter, if found. */
    val totalCounter: Int? = null,
    val source: ParseSource = ParseSource.Static,
    /** How many pages of the slot table were read. */
    val pagesRead: Int = 1,
    /** Rows left out because they were for another date than the one checked. */
    val otherDateRows: Int = 0,
    /** Why the in-app browser wasn't used, when [source] is Static. */
    val note: String? = null,
) {
    val bookedSlots: List<Slot> get() = slots.filter { it.booked }

    val recognized: Boolean get() = bookedCounter != null || slots.isNotEmpty()

    /**
     * Whether "nothing booked" can be believed. An empty slot table while the page doesn't
     * say it has 0 slots means the table never loaded, not that nothing is booked.
     */
    val trustworthy: Boolean
        get() = recognized && (slots.isNotEmpty() || (bookedCounter ?: 0) > 0 || totalCounter == 0)

    /**
     * Booked rows, or the Booked Slots counter if it's higher (a row can be off-screen on another
     * table page). Missing a booking is worse than over-counting.
     */
    val totalBooked: Int get() = maxOf(bookedSlots.size, bookedCounter ?: 0)
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

    private val KNOWN_STATUSES = NOT_BOOKED + setOf("booked", "upcoming", "reserved", "pending", "confirmed", "cancelled")

    private fun isKnownStatus(text: String) = text.trim().lowercase(Locale.US) in KNOWN_STATUSES

    fun isBookedStatus(status: String): Boolean {
        val s = status.trim().lowercase(Locale.US)
        if (s.none { it.isLetter() }) return false // "", "--"
        return s !in NOT_BOOKED
    }

    fun parse(doc: Document, date: LocalDate): ParseResult {
        val counter = findCounter(doc, "Booked Slots")
        val all = readRows(doc)
        val slots = all
            .filter { it.date == null || it.date == date }
            .distinctBy { Triple(it.date, it.start, it.end) }
        return ParseResult(
            bookedCounter = counter,
            slots = slots,
            otherDateRows = all.size - all.count { it.date == null || it.date == date },
            shownDate = findDateInput(doc)?.attr("value")?.trim(),
            totalCounter = findCounter(doc, "Total Slots"),
        )
    }

    /**
     * Combines the pages of a paginated slot table: counters and date from the first page that has
     * them, rows from every page.
     */
    fun merge(pages: List<ParseResult>): ParseResult {
        require(pages.isNotEmpty())
        return ParseResult(
            bookedCounter = pages.firstNotNullOfOrNull { it.bookedCounter },
            slots = pages.flatMap { it.slots }.distinctBy { Triple(it.date, it.start, it.end) }.sortedBy { it.start },
            shownDate = pages.firstNotNullOfOrNull { it.shownDate },
            totalCounter = pages.firstNotNullOfOrNull { it.totalCounter },
            source = pages.first().source,
            otherDateRows = pages.sumOf { it.otherDateRows },
        )
    }

    /**
     * One page of the panel's own data feed (/teacher/pre-assessments/schedule-slots), which the
     * list page uses to draw its table. Times there are UTC ("2026-11-18T12:40:00.000Z"), shown
     * in [zone] (the phone's time zone, Bangladesh).
     */
    fun parseApi(json: String, date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): ApiPage {
        val root = JSONObject(json)
        val data = root.optJSONObject("data") ?: throw LayoutException("The slot feed had no data")
        val summary = data.optJSONObject("summary")
        val details = data.optJSONArray("details") ?: JSONArray()
        val all = (0 until details.length()).mapNotNull { i ->
            val r = details.optJSONObject(i) ?: return@mapNotNull null
            val slot = r.optJSONObject("slotDetails") ?: return@mapNotNull null
            val start = instant(slot.optString("startDateTime"))?.atZone(zone) ?: return@mapNotNull null
            val end = instant(slot.optString("endDateTime"))?.atZone(zone) ?: return@mapNotNull null
            val student = r.optJSONObject("bookingDetails")?.optJSONObject("studentDetails")
            Slot(
                date = start.toLocalDate(),
                start = HM.format(start),
                end = HM.format(end),
                status = slot.optString("status"),
                participant = student?.optString("user_name")?.let(::cleanName),
            )
        }
        val slots = all.filter { it.date == date }
        val meta = root.optJSONObject("meta")
        fun num(o: JSONObject?, key: String) = o?.takeIf { it.has(key) && !it.isNull(key) }?.optInt(key)
        return ApiPage(
            result = ParseResult(
                bookedCounter = num(summary, "totalBookedSlots"),
                slots = slots,
                shownDate = date.format(DMY_FORMAT),
                totalCounter = num(summary, "totalSlots"),
                source = ParseSource.Api,
                otherDateRows = all.size - slots.size,
            ),
            totalPages = num(meta, "totalPages") ?: 1,
        )
    }

    data class ApiPage(val result: ParseResult, val totalPages: Int)

    private val HM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

    private fun instant(s: String): Instant? = runCatching { Instant.parse(s.trim()) }.getOrNull()

    /** The date box in "At a Glance": an input whose value looks like 18-11-2026. */
    fun findDateInput(doc: Document): Element? =
        doc.select("input").firstOrNull { DMY.matches(it.attr("value").trim()) }

    private fun readRows(doc: Document): List<Slot> {
        val out = mutableListOf<Slot>()
        for (table in doc.select("table")) {
            val headers = table.select("thead th, thead td").map { it.text() }
                .ifEmpty { table.selectFirst("tr")?.select("th")?.map { it.text() } ?: emptyList() }
            val statusIdx = headers.indexOfFirst { it.contains("status", ignoreCase = true) }
            val participantIdx = headers.indexOfFirst { it.contains("participant", ignoreCase = true) }
            val timeIdx = headers.indexOfFirst {
                it.contains("time", ignoreCase = true) || it.contains("date", ignoreCase = true)
            }
            val rows = table.select("tbody > tr").ifEmpty { table.select("tr") }
            for (tr in rows) {
                val cells = tr.select("> td")
                if (cells.isEmpty()) continue
                val timeText = if (timeIdx in cells.indices) cells[timeIdx].text() else tr.text()
                val range = TIME_RANGE.find(timeText) ?: TIME_RANGE.find(tr.text()) ?: continue
                // The status badge is the most reliable signal; column positions can be off if the
                // table has hidden or merged columns.
                val status = tr.select(".badge, [class*=badge], .label").firstOrNull { isKnownStatus(it.text()) }?.text()
                    ?: cells.firstOrNull { isKnownStatus(it.text()) }?.text()
                    ?: if (statusIdx in cells.indices) cells[statusIdx].text() else ""
                out += Slot(
                    date = ROW_DATE.find(timeText)?.let(::parseRowDate),
                    start = normalize(range.groupValues[1]),
                    end = normalize(range.groupValues[2]),
                    status = status.trim(),
                    participant = if (participantIdx in cells.indices) cleanName(cells[participantIdx].text()) else null,
                )
            }
        }
        if (out.isEmpty()) out += readLooseRows(doc)
        return out
    }

    /**
     * For a slot list that isn't a <table> (cards or divs, e.g. a mobile layout): every smallest
     * element that holds both a time slot and a status word is one slot.
     */
    private fun readLooseRows(doc: Document): List<Slot> {
        val body = doc.body() ?: return emptyList()
        fun hasStatus(e: Element) = STATUS_WORD.containsMatchIn(e.text())
        fun isRow(e: Element) = TIME_RANGE.containsMatchIn(e.text()) && hasStatus(e)
        return body.select("*")
            .filter { e -> isRow(e) && e.children().none(::isRow) && TIME_RANGE.findAll(e.text()).count() == 1 }
            .mapNotNull { e ->
                val text = e.text()
                val range = TIME_RANGE.find(text) ?: return@mapNotNull null
                val status = e.select("*").firstOrNull { it.ownText().isNotBlank() && isKnownStatus(it.ownText()) }?.ownText()
                    ?: STATUS_WORD.find(text)?.value
                    ?: return@mapNotNull null
                Slot(
                    date = ROW_DATE.find(text)?.let(::parseRowDate),
                    start = normalize(range.groupValues[1]),
                    end = normalize(range.groupValues[2]),
                    status = status.trim(),
                )
            }
    }

    private val STATUS_WORD by lazy {
        Regex("""\b(${KNOWN_STATUSES.joinToString("|")})\b""", RegexOption.IGNORE_CASE)
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

    /** "Rukiya Mahmud" stays; "--", "-" or blank (an empty slot) becomes null. */
    private fun cleanName(raw: String): String? =
        raw.trim().replace(Regex("""\s+"""), " ").takeIf { s -> s.any { it.isLetter() } }

    /** "9:05" -> "09:05" so times compare and sort cleanly. */
    private fun normalize(hhmm: String): String {
        val (h, m) = hhmm.split(":")
        return "%02d:%s".format(Locale.US, h.toInt(), m)
    }
}
