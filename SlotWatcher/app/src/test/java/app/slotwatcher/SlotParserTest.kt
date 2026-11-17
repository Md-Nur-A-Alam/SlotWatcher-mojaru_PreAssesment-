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
