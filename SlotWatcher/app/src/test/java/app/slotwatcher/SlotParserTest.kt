package app.slotwatcher

import app.slotwatcher.data.Format
import app.slotwatcher.net.SlotParser
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** HTML shaped like the Pre Assessment List screenshot (At a Glance cards + slot table). */
class SlotParserTest {

    private val day = LocalDate.of(2026, 11, 18)

    private fun page(booked: Int, rows: String) = Jsoup.parse(
        """
        <div class="card"><h3>At a Glance</h3><input type="text" name="date" value="18-11-2026">
          <div class="stat"><span>Total Slots</span><div class="fs-2">18</div></div>
          <div class="stat"><span>Booked Slots</span><div class="fs-2">$booked</div></div>
          <div class="stat"><span>Available Slots</span><div class="fs-2">${18 - booked}</div></div>
        </div>
        <table>
          <thead><tr><th>Date / Time Slot</th><th>Pre-Assessment Name</th><th>Course</th>
            <th>Slot Status</th><th>Participant Name</th><th>Mobile Number</th><th>Link</th></tr></thead>
          <tbody>$rows</tbody>
        </table>
        """.trimIndent(),
    )

    private fun row(from: String, to: String, status: String) =
        "<tr><td>18 Nov 2026 $from - $to</td><td>Math Competency Test (MCT) for Abacus</td>" +
            "<td>Abacus</td><td><span class=\"badge\">$status</span></td><td>--</td><td>--</td><td>--</td></tr>"

    @Test
    fun `no bookings`() {
        val r = SlotParser.parse(page(0, row("18:30", "18:40", "available") + row("18:40", "18:50", "available")), day)
        assertTrue(r.recognized)
        assertEquals(0, r.bookedCounter)
        assertEquals(2, r.slots.size)
        assertEquals(0, r.totalBooked)
        assertEquals("18-11-2026", r.shownDate)
    }

    @Test
    fun `two booked slots are found with their times`() {
        val rows = row("18:30", "18:40", "booked") + row("18:40", "18:50", "available") +
            row("19:10", "19:20", "upcoming") + row("19:20", "19:30", "expired")
        val r = SlotParser.parse(page(2, rows), day)
        assertEquals(2, r.totalBooked)
        assertEquals(listOf("18:30 - 18:40", "19:10 - 19:20"), r.bookedSlots.map { it.label })
        assertEquals(
            "2 slots booked: 6:30 PM and 7:10 PM.",
            Format.spokenMessage(r.totalBooked, r.bookedSlots.map { it.start }),
        )
    }

    @Test
    fun `rows for another date are ignored`() {
        val other = "<tr><td>19 Nov 2026 18:30 - 18:40</td><td>x</td><td>x</td><td>booked</td><td></td><td></td><td></td></tr>"
        val r = SlotParser.parse(page(0, row("18:30", "18:40", "available") + other), day)
        assertEquals(1, r.slots.size)
        assertEquals(0, r.totalBooked)
    }

    @Test
    fun `counter is the fallback when there is no table`() {
        val doc = Jsoup.parse("<div><span>Booked Slots</span><b>3</b></div>")
        val r = SlotParser.parse(doc, day)
        assertTrue(r.recognized)
        assertEquals(3, r.totalBooked)
    }

    /** The bug in assets/wrong_info.jpeg: raw HTML before the page's JavaScript fills it in. */
    @Test
    fun `empty table with zero counters is not trusted as no bookings`() {
        val raw = Jsoup.parse(
            """
            <div><span>Total Slots</span><div>0</div></div>
            <div><span>Booked Slots</span><div>0</div></div>
            <table><thead><tr><th>Date / Time Slot</th><th>Slot Status</th></tr></thead><tbody></tbody></table>
            """.trimIndent(),
        )
        val r = SlotParser.parse(raw, day)
        assertTrue(r.recognized)
        // Total Slots 0 really means no slots that day...
        assertTrue(r.trustworthy)
        // ...but without a Total Slots counter, an empty table means it didn't load.
        val noTotal = Jsoup.parse("<div><span>Booked Slots</span><div>0</div></div><table><tbody></tbody></table>")
        assertFalse(SlotParser.parse(noTotal, day).trustworthy)
        val withTotal = Jsoup.parse(
            "<div><span>Total Slots</span><div>18</div></div><div><span>Booked Slots</span><div>0</div></div><table><tbody></tbody></table>",
        )
        assertFalse(SlotParser.parse(withTotal, day).trustworthy)
    }

    /** The rendered page from the screenshot: counter says 4, three booked rows visible. */
    @Test
    fun `booked counter higher than visible rows wins`() {
        val rows = row("18:30", "18:40", "booked") + row("18:40", "18:50", "booked") +
            row("18:50", "19:00", "booked") + row("19:00", "19:10", "available")
        val r = SlotParser.parse(page(4, rows), day)
        assertTrue(r.trustworthy)
        assertEquals(3, r.bookedSlots.size)
        assertEquals(4, r.totalBooked)
    }

    private fun namedRow(from: String, to: String, status: String, name: String) =
        "<tr><td>18 Nov 2026 $from - $to</td><td>Math Competency Test (MCT) for Abacus</td>" +
            "<td>Abacus</td><td><span class=\"badge\">$status</span></td><td>$name</td><td>01*******17</td><td>Join</td></tr>"

    /** assets/1st–3rd.jpeg: 18 slots over 2 table pages (15 + 3), Booked Slots 3, one absent. */
    @Test
    fun `two table pages merge and keep participant names`() {
        val page1 = page(
            3,
            namedRow("18:30", "18:40", "absent", "Ahnaf Hossain Abrar / muntaha") +
                namedRow("18:40", "18:50", "booked", "Rukiya Mahmud") +
                namedRow("18:50", "19:00", "booked", "সাবা") +
                namedRow("19:00", "19:10", "booked", "nur afza") +
                (19..20).joinToString("") { h -> row("$h:10", "$h:20", "available") },
        )
        val page2 = page(3, row("21:00", "21:10", "available") + row("21:10", "21:20", "available") + row("21:20", "21:30", "available"))

        val r = SlotParser.merge(listOf(SlotParser.parse(page1, day), SlotParser.parse(page2, day)))
        assertEquals(9, r.slots.size)
        assertEquals(3, r.totalBooked)
        assertEquals(
            listOf("18:40 - 18:50|Rukiya Mahmud", "18:50 - 19:00|সাবা", "19:00 - 19:10|nur afza"),
            r.bookedSlots.map { it.historyLabel },
        )
        // Empty slots have "--" as the participant; that isn't a name.
        assertEquals(null, r.slots.last().participant)
        assertTrue(r.trustworthy)
    }

    /** An extra cell in each row (hidden serial column) shifts columns; the badge still gives the status. */
    @Test
    fun `status comes from the badge even when columns are shifted`() {
        val shifted = Jsoup.parse(
            """
            <div><span>Total Slots</span><div>2</div></div><div><span>Booked Slots</span><div>1</div></div>
            <table><thead><tr><th>Date / Time Slot</th><th>Pre-Assessment Name</th><th>Course</th><th>Slot Status</th><th>Participant Name</th></tr></thead>
            <tbody>
              <tr><td>1</td><td>18 Nov 2026 18:40 - 18:50</td><td>MCT</td><td>অ্যাবাকাস</td><td><span class="badge">booked</span></td><td>Rukiya Mahmud</td></tr>
              <tr><td>2</td><td>18 Nov 2026 18:50 - 19:00</td><td>MCT</td><td>অ্যাবাকাস</td><td><span class="badge">available</span></td><td>--</td></tr>
            </tbody></table>
            """.trimIndent(),
        )
        val r = SlotParser.parse(shifted, day)
        assertEquals(listOf("18:40 - 18:50"), r.bookedSlots.map { it.label })
    }

    /** "Check now" with an old session's date: today's rows were all filtered out, leaving only the count. */
    @Test
    fun `rows for a different date are counted as such`() {
        val r = SlotParser.parse(page(3, row("18:40", "18:50", "booked")), LocalDate.of(2026, 11, 17))
        assertEquals(0, r.slots.size)
        assertEquals(1, r.otherDateRows)
    }

    /** A slot list drawn as cards (no <table>), as some mobile layouts do. */
    @Test
    fun `slots in cards instead of a table are read`() {
        val cards = Jsoup.parse(
            """
            <div><span>Total Slots</span><div>18</div></div><div><span>Booked Slots</span><div>0</div></div>
            <div class="list">
              <div class="card"><div>18 Nov 2026</div><div>18:40 - 18:50</div><div>MCT</div><span class="badge">booked</span></div>
              <div class="card"><div>18 Nov 2026</div><div>18:50 - 19:00</div><div>MCT</div><span class="badge">available</span></div>
            </div>
            """.trimIndent(),
        )
        val r = SlotParser.parse(cards, day)
        assertEquals(2, r.slots.size)
        assertEquals(listOf("18:40 - 18:50"), r.bookedSlots.map { it.label })
        assertTrue(r.trustworthy)
    }

    @Test
    fun `unrelated page is not recognized`() {
        assertFalse(SlotParser.parse(Jsoup.parse("<h1>Dashboard</h1>"), day).recognized)
    }

    @Test
    fun statuses() {
        assertFalse(SlotParser.isBookedStatus("available"))
        assertFalse(SlotParser.isBookedStatus(" Expired "))
        assertFalse(SlotParser.isBookedStatus("--"))
        assertTrue(SlotParser.isBookedStatus("booked"))
        assertTrue(SlotParser.isBookedStatus("Upcoming"))
    }
}
